package com.miguno.kafkastorm.storm.topologies

import java.util.Properties

import backtype.storm.generated.KillOptions
import backtype.storm.topology.TopologyBuilder
import backtype.storm.{Config, LocalCluster}
import com.miguno.kafkastorm.kafka.KafkaEmbedded
import com.miguno.kafkastorm.zookeeper.ZooKeeperEmbedded
import kafka.admin.AdminUtils
import kafka.utils.ZKStringSerializer
import org.I0Itec.zkclient.ZkClient
import storm.kafka.{KafkaSpout, SpoutConfig, ZkHosts}

import scala.concurrent.duration._

/**
 * Showcases how to create a Storm topology that reads data from Kafka.  Because it's a demo this topology does not
 * (yet?) do anything to the input data -- it just reads, that's it.  If you want to add functionality you only need to
 * put one or more Storm bolts after the spout that reads from Kafka.
 *
 * The default setup runs the topology against an in-memory instance of Kafka (that is backed by an in-memory instance
 * of ZooKeeper).  Alternatively, you can also point the topology to a "real" Kafka cluster.  An easy and quick way to
 * deploy such a Kafka and ZooKeeper infrastructure is to use a tool such as
 * [[https://github.com/miguno/wirbelsturm Wirbelsturm]].
 */
class KafkaStormDemo(kafkaZkConnect: String, topic: String, numTopicPartitions: Int = 1,
                     topologyName: String = "kafka-storm-starter", runtime: Duration = 1.hour) {

  def runTopologyLocally() {
    val zkHosts = new ZkHosts(kafkaZkConnect)
    val topic = "testing"
    val zkRoot = "/kafka-spout"
    // The spout appends this id to zkRoot when composing its ZooKeeper path.  You don't need a leading `/`.
    val zkSpoutId = "kafka-storm-starter"
    val kafkaConfig = new SpoutConfig(zkHosts, topic, zkRoot, zkSpoutId)
    val kafkaSpout = new KafkaSpout(kafkaConfig)
    val numSpoutExecutors = numTopicPartitions
    val builder = new TopologyBuilder
    val spoutId = "kafka-spout"
    builder.setSpout(spoutId, kafkaSpout, numSpoutExecutors)

    // Showcases how to customize the topology configuration
    val topologyConfiguration = {
      val c = new Config
      c.setDebug(false)
      c.setNumWorkers(4)
      c.setMaxSpoutPending(1000)
      c.setMessageTimeoutSecs(60)
      c.setNumAckers(0)
      c.setMaxTaskParallelism(50)
      c.put(Config.TOPOLOGY_EXECUTOR_RECEIVE_BUFFER_SIZE, 16384: Integer)
      c.put(Config.TOPOLOGY_EXECUTOR_SEND_BUFFER_SIZE, 16384: Integer)
      c.put(Config.TOPOLOGY_RECEIVER_BUFFER_SIZE, 8: Integer)
      c.put(Config.TOPOLOGY_TRANSFER_BUFFER_SIZE, 32: Integer)
      c.put(Config.TOPOLOGY_STATS_SAMPLE_RATE, 0.05: java.lang.Double)
      c
    }

    // Now run the topology in a local, in-memory Storm cluster
    val cluster = new LocalCluster
    cluster.submitTopology(topologyName, topologyConfiguration, builder.createTopology())
    Thread.sleep(runtime.toMillis)
    val killOpts = new KillOptions()
    killOpts.set_wait_secs(1)
    cluster.killTopologyWithOpts(topologyName, killOpts)
    cluster.shutdown()
  }

}

object KafkaStormDemo {

  private var zookeeperEmbedded: Option[ZooKeeperEmbedded] = None
  private var zkClient: Option[ZkClient] = None
  private var kafkaEmbedded: Option[KafkaEmbedded] = None

  def main(args: Array[String]) {
    val kafkaTopic = "testing"
    startZooKeeperAndKafka(kafkaTopic)
    for {z <- zookeeperEmbedded} {
      val topology = new KafkaStormDemo(z.connectString, kafkaTopic)
      topology.runTopologyLocally()
    }
    stopZooKeeperAndKafka()
  }

  /**
   * Launches in-memory, embedded instances of ZooKeeper and Kafka so that our demo Storm topology can connect to and
   * read from Kafka.
   */
  private def startZooKeeperAndKafka(topic: String, numTopicPartitions: Int = 1, numTopicReplicationFactor: Int = 1,
                                     zookeeperPort: Int = 2181) {

    zookeeperEmbedded = Some(new ZooKeeperEmbedded(zookeeperPort))
    for {z <- zookeeperEmbedded} {
      val brokerConfig = new Properties
      brokerConfig.put("zookeeper.connect", z.connectString)
      kafkaEmbedded = Some(new KafkaEmbedded(brokerConfig))
      for {k <- kafkaEmbedded} {
        k.start()
      }

      val sessionTimeout = 30.seconds
      val connectionTimeout = 30.seconds
      zkClient = Some(new ZkClient(z.connectString, sessionTimeout.toMillis.toInt, connectionTimeout.toMillis.toInt,
        ZKStringSerializer))
      for {
        zc <- zkClient
      } {
        val topicConfig = new Properties
        AdminUtils.createTopic(zc, topic, numTopicPartitions, numTopicReplicationFactor, topicConfig)
      }
    }
  }

  private def stopZooKeeperAndKafka() {
    for {k <- kafkaEmbedded} k.stop()
    for {zc <- zkClient} zc.close()
    for {z <- zookeeperEmbedded} z.stop()
  }

}