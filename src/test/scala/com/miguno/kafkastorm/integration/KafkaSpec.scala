package com.miguno.kafkastorm.integration

import _root_.kafka.message.MessageAndMetadata
import _root_.kafka.utils.{Logging, ZKStringSerializer}
import com.miguno.avro.Tweet
import com.miguno.kafkastorm.kafka.{KafkaProducerApp, ConsumerTaskContext, KafkaConsumer, KafkaEmbedded}
import com.miguno.kafkastorm.zookeeper.ZooKeeperEmbedded
import com.twitter.bijection.Injection
import com.twitter.bijection.avro.SpecificAvroCodecs
import java.util.Properties
import org.I0Itec.zkclient.ZkClient
import org.apache.curator.test.InstanceSpec
import org.scalatest._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.reflectiveCalls
import kafka.admin.AdminUtils

@DoNotDiscover
class KafkaSpec extends FunSpec with Matchers with BeforeAndAfterAll with GivenWhenThen with Logging {

  private val testTopic = "testing"
  private val testTopicNumPartitions = 1
  private val testTopicReplicationFactor = 1
  private val zookeeperPort = InstanceSpec.getRandomPort

  private var zookeeperEmbedded: Option[ZooKeeperEmbedded] = None
  private var zkClient: Option[ZkClient] = None
  private var kafkaEmbedded: Option[KafkaEmbedded] = None

  implicit val specificAvroBinaryInjectionForTweet = SpecificAvroCodecs.toBinary[Tweet]

  override def beforeAll() {
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

      // Create test topic
      val sessionTimeout = 30.seconds
      val connectionTimeout = 30.seconds
      zkClient = Some(new ZkClient(z.connectString, sessionTimeout.toMillis.toInt, connectionTimeout.toMillis.toInt,
        ZKStringSerializer))
      for {
        zc <- zkClient
      } {
        val topicConfig = new Properties
        AdminUtils.createTopic(zc, testTopic, testTopicNumPartitions, testTopicReplicationFactor, topicConfig)
      }
    }
  }

  override def afterAll() {
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

  describe("Kafka") {

    it("should synchronously send and receive a Tweet in Avro format", IntegrationTest) {
      for {
        z <- zookeeperEmbedded
        k <- kafkaEmbedded
      } {
        Given("a ZooKeeper instance")
        And("a Kafka broker instance")
        And("some tweets")
        val f = fixture
        val tweets = f.messages
        And("a single-threaded Kafka consumer group")
        // The Kafka consumer group must be running before the first messages are being sent to the topic.
        val numConsumerThreads = 1
        val consumerConfig = {
          val c = new Properties
          c.put("group.id", "test-consumer")
          c
        }
        val consumer = new KafkaConsumer(testTopic, z.connectString, numConsumerThreads, consumerConfig)
        val actualTweets = new mutable.SynchronizedQueue[Tweet]
        consumer.startConsumers(
          (m: MessageAndMetadata[Array[Byte], Array[Byte]], c: ConsumerTaskContext) => {
            val tweet = Injection.invert[Tweet, Array[Byte]](m.message)
            for {t <- tweet} {
              info(s"Consumer thread ${c.threadId}: received Tweet ${t} from partition ${m.partition} of topic ${m.topic} (offset: ${m.offset})")
              actualTweets += t
            }
          })
        val waitForConsumerStartup = 300.millis
        debug(s"Waiting $waitForConsumerStartup for Kafka consumer threads to launch")
        Thread.sleep(waitForConsumerStartup.toMillis)
        debug("Finished waiting for Kafka consumer threads to launch")

        When("I start a synchronous Kafka producer that sends the tweets in Avro binary format")
        val syncProducerConfig = {
          val c = new Properties
          c.put("producer.type", "sync")
          c.put("client.id", "test-sync-producer")
          c.put("request.required.acks", "1")
          c
        }
        val producerApp = new KafkaProducerApp(testTopic, k.brokerList, syncProducerConfig)
        tweets foreach {
          case tweet => {
            val bytes = Injection[Tweet, Array[Byte]](tweet)
            info(s"Synchronously sending Tweet $tweet to topic ${producerApp.topic}")
            producerApp.send(bytes)
          }
        }

        Then("the consumer app should receive the tweets")
        val waitForConsumerToReadStormOutput = 300.millis
        debug(s"Waiting $waitForConsumerToReadStormOutput for Kafka consumer threads to read messages")
        Thread.sleep(waitForConsumerToReadStormOutput.toMillis)
        debug("Finished waiting for Kafka consumer threads to read messages")
        actualTweets.toSeq should be(f.messages.toSeq)

        // Cleanup
        debug("Shutting down Kafka consumer threads")
        consumer.shutdown()
        debug("Shutting down Kafka producer app")
        producerApp.shutdown()
      }
    }

    it("should asynchronously send and receive a Tweet in Avro format", IntegrationTest) {
      for {
        z <- zookeeperEmbedded
        k <- kafkaEmbedded
      } {
        Given("a ZooKeeper instance")
        And("a Kafka broker instance")
        And("some tweets")
        val f = fixture
        val tweets = f.messages
        And("a single-threaded Kafka consumer group")
        // The Kafka consumer group must be running before the first messages are being sent to the topic.
        val numConsumerThreads = 1
        val consumerConfig = {
          val c = new Properties
          c.put("group.id", "test-consumer")
          c
        }
        val consumer = new KafkaConsumer(testTopic, z.connectString, numConsumerThreads, consumerConfig)
        val actualTweets = new mutable.SynchronizedQueue[Tweet]
        consumer.startConsumers(
          (m: MessageAndMetadata[Array[Byte], Array[Byte]], c: ConsumerTaskContext) => {
            val tweet = Injection.invert[Tweet, Array[Byte]](m.message)
            for {t <- tweet} {
              info(s"Consumer thread ${c.threadId}: received Tweet ${t} from partition ${m.partition} of topic ${m.topic} (offset: ${m.offset})")
              actualTweets += t
            }
          })
        val waitForConsumerStartup = 300.millis
        debug(s"Waiting $waitForConsumerStartup for Kafka consumer threads to launch")
        Thread.sleep(waitForConsumerStartup.toMillis)
        debug("Finished waiting for Kafka consumer threads to launch")

        When("I start an asynchronous Kafka producer that sends the tweets in Avro binary format")
        val syncProducerConfig = {
          val c = new Properties
          c.put("producer.type", "async")
          c.put("client.id", "test-sync-producer")
          c.put("request.required.acks", "1")
          // We must set `batch.num.messages` and/or `queue.buffering.max.ms` so that the async producer will actually
          // send our (typically few) test messages before the unit test finishes.
          c.put("batch.num.messages", tweets.size.toString)
          c
        }
        val producerApp = new KafkaProducerApp(testTopic, k.brokerList, syncProducerConfig)
        tweets foreach {
          case tweet => {
            val bytes = Injection[Tweet, Array[Byte]](tweet)
            info(s"Asynchronously sending Tweet $tweet to topic ${producerApp.topic}")
            producerApp.send(bytes)
          }
        }

        Then("the consumer app should receive the tweets")
        val waitForConsumerToReadStormOutput = 300.millis
        debug(s"Waiting $waitForConsumerToReadStormOutput for Kafka consumer threads to read messages")
        Thread.sleep(waitForConsumerToReadStormOutput.toMillis)
        debug("Finished waiting for Kafka consumer threads to read messages")
        actualTweets.toSeq should be(f.messages.toSeq)

        // Cleanup
        debug("Shutting down Kafka consumer threads")
        consumer.shutdown()
        debug("Shutting down Kafka producer app")
        producerApp.shutdown()
      }
    }

  }

}