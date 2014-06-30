package com.miguno.kafkastorm.integration

import kafka.admin.AdminUtils
import _root_.kafka.utils.{Logging, ZKStringSerializer}
import _root_.storm.kafka.{KafkaSpout, SpoutConfig, ZkHosts}
import backtype.storm.{Testing, ILocalCluster, Config}
import backtype.storm.generated.StormTopology
import backtype.storm.spout.SchemeAsMultiScheme
import backtype.storm.testing._
import backtype.storm.topology.TopologyBuilder
import com.miguno.avro.Tweet
import com.miguno.kafkastorm.kafka._
import com.miguno.kafkastorm.storm.{AvroDecoderBolt, AvroKafkaSinkBolt, AvroScheme, TweetAvroKryoDecorator}
import com.miguno.kafkastorm.zookeeper.ZooKeeperEmbedded
import com.twitter.bijection.Injection
import com.twitter.bijection.avro.SpecificAvroCodecs
import java.util.Properties
import kafka.message.MessageAndMetadata
import org.I0Itec.zkclient.ZkClient
import org.apache.curator.test.InstanceSpec
import org.scalatest._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.reflectiveCalls

/**
 * This Kafka/Storm integration test code is slightly more complicated than the other tests in this project.  This is
 * due to a number of reasons, such as:  the way Storm topologies are "wired" and configured, the test facilities
 * exposed by Storm, and -- on a higher level -- because there are quite a number of components involved (ZooKeeper,
 * Kafka producers and consumers, Storm) which must be set up, run, and terminated in the correct order.  For these
 * reasons the integration tests are not simple "given/when/then" style tests.
 */
@DoNotDiscover
class KafkaStormSpec extends FeatureSpec with Matchers with BeforeAndAfterEach with GivenWhenThen with Logging {

  implicit val specificAvroBinaryInjectionForTweet = SpecificAvroCodecs.toBinary[Tweet]

  private val inputTopic = "testing-input"
  private val inputTopicNumPartitions = 1
  private val inputTopicReplicationFactor = 1
  private val outputTopic = "testing-output"
  private val outputTopicNumPartitions = 1
  private val outputTopicReplicationFactor = 1
  private val zookeeperPort = InstanceSpec.getRandomPort
  private var zookeeperEmbedded: Option[ZooKeeperEmbedded] = None
  private var zkClient: Option[ZkClient] = None
  private var kafkaEmbedded: Option[KafkaEmbedded] = None

  override def beforeEach() {
    // Start embedded ZooKeeper server
    zookeeperEmbedded = Some(new ZooKeeperEmbedded(zookeeperPort))

    for {z <- zookeeperEmbedded} {
      // Start embedded Kafka broker
      val brokerConfig = new Properties
      brokerConfig.put("zookeeper.connect", z.connectString)
      kafkaEmbedded = Some(new KafkaEmbedded(brokerConfig))
      for {k <- kafkaEmbedded} {
        k.start()
      }

      // Create test topics
      val sessionTimeout = 30.seconds
      val connectionTimeout = 30.seconds
      // Note: You must initialize the ZkClient with ZKStringSerializer.  If you don't, then createTopic() will only
      // seem to work (it will return without error).  Topic will exist in only ZooKeeper, and will be returned when
      // listing topics, but Kafka itself does not create the topic.
      zkClient = Some(new ZkClient(z.connectString, sessionTimeout.toMillis.toInt, connectionTimeout.toMillis.toInt,
        ZKStringSerializer))
      for {
        zc <- zkClient
      } {
        val inputTopicConfig = new Properties
        AdminUtils.createTopic(zc, inputTopic, inputTopicNumPartitions, inputTopicReplicationFactor, inputTopicConfig)
        val outputTopicConfig = new Properties
        AdminUtils.createTopic(zc, outputTopic, outputTopicNumPartitions, outputTopicReplicationFactor,
          outputTopicConfig)
      }
    }
  }

  override def afterEach() {
    for {k <- kafkaEmbedded} k.stop()

    for {
      zc <- zkClient
    } {
      info("ZooKeeper client: shutting down...")
      zc.close()
      info("ZooKeeper client: shutdown completed")
    }

    for {z <- zookeeperEmbedded} z.stop()
  }

  val fixture = {
    val BeginningOfEpoch = 0.seconds
    val AnyTimestamp = 1234.seconds
    val now = System.currentTimeMillis().millis

    new {
      val t1 = new Tweet("ANY_USER_1", "ANY_TEXT_1", now.toSeconds)
      val t2 = new Tweet("ANY_USER_2", "ANY_TEXT_2", BeginningOfEpoch.toSeconds)
      val t3 = new Tweet("ANY_USER_3", "ANY_TEXT_3", AnyTimestamp.toSeconds)

      val messages = Seq(t1, t2, t3)
    }
  }

  info("As a user of Storm")
  info("I want to read Avro-encoded data from Kafka")
  info("so that I can quickly build Kafka<->Storm data flows")

  feature("AvroDecoderBolt[T]") {

    scenario("User creates a Storm topology that uses AvroDecoderBolt", IntegrationTest) {
      for {
        k <- kafkaEmbedded
        z <- zookeeperEmbedded
      } {
        Given("a ZooKeeper instance")
        And("a Kafka broker instance")
        And(s"a Storm topology that uses AvroDecoderBolt and that reads tweets from topic $inputTopic and writes " +
          s"them as-is to topic $outputTopic")
        // We create a topology instance that makes use of an Avro decoder bolt to deserialize the Kafka spout's output
        // into pojos.  Here, the data flow is KafkaSpout -> AvroDecoderBolt -> AvroKafkaSinkBolt.
        val builder = new TopologyBuilder
        val kafkaSpoutId = "kafka-spout"
        val kafkaSpoutConfig = kafkaSpoutBaseConfig(z.connectString, inputTopic)
        val kafkaSpout = new KafkaSpout(kafkaSpoutConfig)
        val numSpoutExecutors = inputTopicNumPartitions
        builder.setSpout(kafkaSpoutId, kafkaSpout, numSpoutExecutors)

        val decoderBoltId = "avro-decoder-bolt"
        val decoderBolt = new AvroDecoderBolt[Tweet]
        // Note: Should test messages arrive out-of-order, we may want to enforce a parallelism of 1 for this bolt.
        builder.setBolt(decoderBoltId, decoderBolt).globalGrouping(kafkaSpoutId)

        val kafkaSinkBoltId = "avro-kafka-sink-bolt"
        val producerAppFactory = new BaseKafkaProducerAppFactory(outputTopic, k.brokerList)
        val kafkaSinkBolt = new AvroKafkaSinkBolt[Tweet](producerAppFactory)
        // Note: Should test messages arrive out-of-order, we may want to enforce a parallelism of 1 for this bolt.
        builder.setBolt(kafkaSinkBoltId, kafkaSinkBolt).globalGrouping(decoderBoltId)
        val topology = builder.createTopology()

        baseIntegrationTest(z, k, topology, inputTopic, outputTopic)
      }
    }
  }

  feature("AvroScheme[T] for Kafka spout") {
    scenario("User creates a Storm topology that uses AvroScheme in Kafka spout", IntegrationTest) {
      for {
        k <- kafkaEmbedded
        z <- zookeeperEmbedded
      } {
        Given("a ZooKeeper instance")
        And("a Kafka broker instance")
        And(s"a Storm topology that uses AvroScheme and that reads tweets from topic $inputTopic and writes them " +
          s"as-is to topic $outputTopic")
        // Creates a topology instance that adds an Avro decoder "scheme" to the Kafka spout, so that the spout's output
        // are ready-to-use pojos.  Here, the data flow is KafkaSpout -> AvroKafkaSinkBolt.
        //
        // Note that Storm will still need to re-serialize the spout's pojo output to send the data across the wire to
        // downstream consumers/bolts, which will then deserialize the data again.  In our case we have a custom Kryo
        // serializer registered with Storm to make this serde step as fast as possible.
        val builder = new TopologyBuilder
        val kafkaSpoutId = "kafka-spout"
        val kafkaSpoutConfig = kafkaSpoutBaseConfig(z.connectString, inputTopic)
        // You can provide the Kafka spout with a custom `Scheme` to deserialize incoming messages in a particular way.
        // The default scheme is Storm's `backtype.storm.spout.RawMultiScheme`, which simply returns the raw bytes of the
        // incoming data (i.e. leaving deserialization up to you).  In this example, we configure the spout to use
        // a custom scheme, AvroScheme[Tweet], which will modify the spout to automatically deserialize incoming data
        // into pojos.
        kafkaSpoutConfig.scheme = new SchemeAsMultiScheme(new AvroScheme[Tweet])
        val kafkaSpout = new KafkaSpout(kafkaSpoutConfig)
        val numSpoutExecutors = inputTopicNumPartitions
        builder.setSpout(kafkaSpoutId, kafkaSpout, numSpoutExecutors)

        val kafkaSinkBoltId = "avro-kafka-sink-bolt"
        val producerAppFactory = new BaseKafkaProducerAppFactory(outputTopic, k.brokerList)
        val kafkaSinkBolt = new AvroKafkaSinkBolt[Tweet](producerAppFactory)
        // Note: Should test messages arrive out-of-order, we may want to enforce a parallelism of 1 for this bolt.
        builder.setBolt(kafkaSinkBoltId, kafkaSinkBolt).globalGrouping(kafkaSpoutId)
        val topology = builder.createTopology()

        baseIntegrationTest(z, k, topology, inputTopic, outputTopic)
      }
    }
  }

  private def kafkaSpoutBaseConfig(zookeeperConnect: String, inputTopic: String): SpoutConfig = {
    val zkHosts = new ZkHosts(zookeeperConnect)
    val zkRoot = "/kafka-storm-starter-spout"
    //  This id is appended to zkRoot for constructing a ZK path under which the spout stores partition information.
    val zkId = "kafka-spout"
    // To configure the spout to read from the very beginning of the topic (auto.offset.reset = smallest), you can use
    // either of the following two equivalent approaches:
    //
    //    1. spoutConfig.startOffsetTime = kafka.api.OffsetRequest.EarliestTime
    //    2. spoutConfig.forceFromStart = true
    //
    // To configure the spout to read from the end of the topic (auto.offset.reset = largest), you can use either of
    // the following two equivalent approaches:
    //
    //    1. Do nothing -- reading from the end of the topic is the default behavior.
    //    2. spoutConfig.startOffsetTime = kafka.api.OffsetRequest.LatestTime
    //
    val spoutConfig = new SpoutConfig(zkHosts, inputTopic, zkRoot, zkId)
    spoutConfig
  }

  /**
   * This method sends Avro-encoded test data into a Kafka "input" topic.  This data is read from Kafka into Storm,
   * which will then decode and re-encode the data, and then write the data to an "output" topic in Kafka (which is our
   * means/workaround to "tap into" Storm's output, as we haven't been able yet to use Storm's built-in testing
   * facilities for such integration tests).  Lastly, we read the data from the "output" topic via a Kafka consumer
   * group, and then compare the output data with the input data, with the latter serving the dual purpose of also
   * being the expected output data.
   */
  private def baseIntegrationTest(zookeeper: ZooKeeperEmbedded, kafka: KafkaEmbedded, topology: StormTopology,
    inputTopic: String, outputTopic: String) {
    And("some tweets")
    val f = fixture
    val tweets = f.messages

    And(s"a synchronous Kafka producer app that writes to the topic $inputTopic")
    val kafkaSyncProducerConfig = {
      val c = new Properties
      c.put("producer.type", "sync")
      c.put("client.id", "kafka-storm-test-sync-producer")
      c.put("request.required.acks", "1")
      c
    }
    val producerApp = new KafkaProducerApp(inputTopic, kafka.brokerList, kafkaSyncProducerConfig)

    And(s"a single-threaded Kafka consumer app that reads from topic $outputTopic and Avro-decodes the incoming data")
    // We start the Kafka consumer group, which (in our case) must be running before the first messages are being sent
    // to the output Kafka topic.  The Storm topology will write its output to this topic.  We use the Kafka consumer
    // group to learn which data was created by Storm, and compare this actual output data to the expected data (which
    // in our case is the original input data).
    val numConsumerThreads = 1
    val kafkaConsumerConfig = {
      val c = new Properties
      c.put("group.id", "kafka-storm-test-consumer")
      c
    }
    val consumer = new KafkaConsumer(outputTopic, zookeeper.connectString, numConsumerThreads, kafkaConsumerConfig)
    val actualTweets = new mutable.SynchronizedQueue[Tweet]
    consumer.startConsumers(
      (m: MessageAndMetadata[Array[Byte], Array[Byte]], c: ConsumerTaskContext) => {
        val tweet = Injection.invert[Tweet, Array[Byte]](m.message())
        for {t <- tweet} {
          info(s"Consumer thread ${c.threadId}: received Tweet $t from partition ${m.partition} of topic ${m.topic} " +
            s"(offset: ${m.offset})")
          actualTweets += t
        }
      })
    val waitForConsumerStartup = 300.millis
    Thread.sleep(waitForConsumerStartup.toMillis)

    And("a Storm topology configuration that registers an Avro Kryo decorator for Tweet")
    // We create the topology configuration here simply to clarify that it is part of the test's initial context defined
    // under "Given".
    val topologyConfig = {
      val conf = new Config
      // Use more than one worker thread.  It looks as if serialization occurs only if you have actual parallelism in
      // LocalCluster (i.e. numWorkers > 1).
      conf.setNumWorkers(2)
      // Never use Java's default serialization.  This allows us to see whether Kryo serialization is properly
      // configured and working for all types.
      conf.setFallBackOnJavaSerialization(false)
      // Serialization config, see http://storm.incubator.apache.org/documentation/Serialization.html
      // Note: We haven't been able yet to come up with a KryoDecorator[Tweet] approach.
      conf.registerDecorator(classOf[TweetAvroKryoDecorator])
      conf
    }

    When("I run the Storm topology")
    val stormTestClusterParameters = {
      val mkClusterParam = new MkClusterParam
      mkClusterParam.setSupervisors(2)
      val daemonConf = new Config
      mkClusterParam.setDaemonConf(daemonConf)
      mkClusterParam
    }
    Testing.withLocalCluster(stormTestClusterParameters, new TestJob() {
      override def run(stormCluster: ILocalCluster) {
        val topologyName = "storm-kafka-integration-test"
        stormCluster.submitTopology(topologyName, topologyConfig, topology)
        val waitForTopologyStartupMs = 3.seconds.toMillis
        Thread.sleep(waitForTopologyStartupMs)

        And("I Avro-encode the tweets and use the Kafka producer app to sent them to Kafka")
        tweets foreach {
          case tweet =>
            val bytes = Injection[Tweet, Array[Byte]](tweet)
            info(s"Synchronously sending Tweet $tweet to topic ${producerApp.topic}")
            producerApp.send(bytes)
        }

        val waitForStormToReadFromKafka = 1.seconds
        Thread.sleep(waitForStormToReadFromKafka.toMillis)
      }
    })

    Then("the Kafka consumer app should receive the original tweets from the Storm topology")
    val waitForConsumerToReadStormOutput = 300.millis
    Thread.sleep(waitForConsumerToReadStormOutput.toMillis)
    actualTweets.toSeq should be(tweets.toSeq)

    // Cleanup
    consumer.shutdown()
    producerApp.shutdown()
  }

}