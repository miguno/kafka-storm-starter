package com.miguno.kafkastorm.testing

import java.util.Properties

import com.miguno.kafkastorm.kafka.{ConsumerTaskContext, KafkaConsumerApp, KafkaEmbedded, KafkaProducerApp}
import com.miguno.kafkastorm.logging.LazyLogging
import com.miguno.kafkastorm.zookeeper.ZooKeeperEmbedded
import kafka.message.MessageAndMetadata
import org.apache.curator.test.InstanceSpec

import scala.concurrent.duration._
import scala.util.{Success, Try}

/**
 * Starts embedded instances of a Kafka broker and a ZooKeeper server.  Used only for testing.
 *
 * @param zookeeperPort ZooKeeper port
 * @param kafkaPort: Kafka Port
 * @param topics Kafka topics to be created
 * @param brokerConfig Kafka broker configuration
 */
class EmbeddedKafkaZooKeeperCluster(zookeeperPort: Integer = InstanceSpec.getRandomPort,
                                    kafkaPort: Integer = InstanceSpec.getRandomPort,
                                    topics: Seq[KafkaTopic] = Seq(),
                                    brokerConfig: Properties = new Properties) extends LazyLogging {

  type Key = Array[Byte]
  type Val = Array[Byte]

  var zookeeper: ZooKeeperEmbedded = _
  var kafka: KafkaEmbedded = _

  val consumerApps = collection.mutable.Buffer[KafkaConsumerApp[_]]()
  val producerApps = collection.mutable.Buffer[KafkaProducerApp]()

  // We intentionally use a fail-fast approach here.  This makes the downstream test code simpler because we want our
  // tests to fail immediately in case we run into problems here.
  // TODO: Do we need idempotency for the start() method, i.e. don't let clients run start() twice?
  def start() {
    // Start embedded ZooKeeper server
    zookeeper = new ZooKeeperEmbedded(zookeeperPort)

    // Start embedded Kafka broker
    kafka = {
      val config = {
        val p = new Properties
        p.put("zookeeper.connect", zookeeper.connectString)
        p.put("port", kafkaPort.toString)
        p.putAll(brokerConfig)
        p
      }
      new KafkaEmbedded(config)
    }
    kafka.start()
    createTopics()
  }

  private def createTopics() {
    if (topics.nonEmpty) {
      for (topic <- topics) {
        kafka.createTopic(topic.name, topic.partitions, topic.replicationFactor, topic.config)
      }
    }
  }

  def stop() {
    logger.info(s"Shutting down ${consumerApps.length} consumer apps")
    for (app <- consumerApps) {
      Try(app.shutdown())
    }

    logger.info(s"Shutting down ${producerApps.length} producer apps")
    for (producer <- producerApps) {
      Try(producer.shutdown())
    }
    kafka.stop()
    zookeeper.stop()
  }

  /**
   * For the moment we only allow the creation of new producers if the backing Kafka broker is up and running.
   *
   * @param topic Kafka topic to write to.
   * @param config
   * @return
   */
  def createProducer(topic: String, config: Properties): Try[KafkaProducerApp] = {
    val producer = new KafkaProducerApp(kafka.brokerList, config, Option(topic))
    producerApps += producer
    Success(producer)
  }

  /**
   * For the moment we only allow the creation of new consumers if the backing ZooKeeper server is up and running.
   *
   * @param topic Kafka topic to read from.
   * @param consume
   * @tparam T
   * @return
   */
  def createAndStartConsumer[T](topic: String,
                                consume: (MessageAndMetadata[Key, Val], ConsumerTaskContext) => Unit): Try[KafkaConsumerApp[T]] = {
    val consumerApp = {
      val numStreams = 1
      val config = {
        val c = new Properties
        c.put("group.id", "kafka-storm-starter-test-consumer")
        c
      }
      new KafkaConsumerApp[T](topic, zookeeper.connectString, numStreams, config)
    }
    consumerApp.startConsumers(f = (m: MessageAndMetadata[Key, Val], c: ConsumerTaskContext, n: Option[T]) => consume(m, c))
    val waitForConsumerStartup = 300.millis
    Thread.sleep(waitForConsumerStartup.toMillis)

    consumerApps += consumerApp
    logger.debug(s"Consumer app created for topic: $topic. Total consumers running: ${consumerApps.size}")
    Success(consumerApp)
  }

}

case class KafkaTopic(name: String, partitions: Integer = 1, replicationFactor: Integer = 1,
                      config: Properties = new Properties) {

  require(partitions > 0)
  require(replicationFactor > 0)

}