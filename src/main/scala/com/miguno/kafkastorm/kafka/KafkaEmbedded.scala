package com.miguno.kafkastorm.kafka

import java.util.Properties
import kafka.server.{KafkaServerStartable, KafkaConfig}
import kafka.utils.Logging

/**
 * Runs an in-memory, "embedded" instance of a Kafka broker, which listens at `127.0.0.1:9092`.
 *
 * Requires a running ZooKeeper instance to connect to.  By default, it expects a ZooKeeper instance running at
 * `127.0.0.1:2181`.  You can specify a different ZooKeeper instance by setting the `zookeeper.connect` parameter in the
 * broker's configuration.
 *
 * @param config Broker configuration settings.
 */
class KafkaEmbedded(config: Properties = new Properties) extends Logging {

  private val defaultZkConnect = "127.0.0.1:2181"

  private val effectiveConfig = {
    val c = new Properties
    c.load(this.getClass.getResourceAsStream("/broker-defaults.properties"))
    c.putAll(config)
    c
  }

  private val kafkaConfig = new KafkaConfig(effectiveConfig)
  private val kafka = new KafkaServerStartable(kafkaConfig)

  /**
   * This broker's `metadata.broker.list` value.  Example: `127.0.0.1:9092`.
   *
   * You can use this to tell Kafka producers and consumers how to connect to this instance.
   */
  val brokerList = kafka.serverConfig.hostName + ":" + kafka.serverConfig.port

  /**
   * The ZooKeeper connection string aka `zookeeper.connect`.
   */
  val zookeeperConnect = {
    val zkConnectLookup = Option(effectiveConfig.getProperty("zookeeper.connect"))
    zkConnectLookup match {
      case Some(zkConnect) => zkConnect
      case _ =>
        warn(s"zookeeper.connect is not configured -- falling back to default setting $defaultZkConnect")
        defaultZkConnect
    }
  }

  /**
   * Start the broker.
   */
  def start() {
    debug(s"Starting embedded Kafka broker at $brokerList (using ZooKeeper server at $zookeeperConnect) ...")
    kafka.startup()
    debug("Embedded Kafka broker startup completed")
  }

  /**
   * Stop the broker.
   */
  def stop() {
    debug("Shutting down embedded Kafka broker...")
    kafka.shutdown()
    debug("Embedded Kafka broker shutdown completed")
  }

}