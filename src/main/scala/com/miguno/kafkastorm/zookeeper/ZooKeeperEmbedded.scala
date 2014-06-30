package com.miguno.kafkastorm.zookeeper

import kafka.utils.Logging
import org.apache.curator.test.TestingServer

/**
 * Runs an in-memory, "embedded" instance of a ZooKeeper server.
 *
 * The ZooKeeper server instance is automatically started when you create a new instance of this class.
 *
 * @param port The port (aka `clientPort`) to listen to.  Default: 2181.
 */
class ZooKeeperEmbedded(port: Int) extends Logging {

  debug(s"Starting embedded ZooKeeper server on port ${port}...")

  private val server = new TestingServer(port)

  /**
   * Stop the instance.
   */
  def stop() {
    debug("Shutting down embedded ZooKeeper server...")
    server.close()
    debug("Embedded ZooKeeper server shutdown completed")
  }

  /**
   * The ZooKeeper connection string aka `zookeeper.connect` in `hostnameOrIp:port` format.
   * Example: `127.0.0.1:2181`.
   *
   * You can use this to e.g. tell Kafka and Storm how to connect to this instance.
   */
  val connectString = server.getConnectString

}