package com.miguno.kafkastorm.storm.utils

import backtype.storm.{Config, StormSubmitter, LocalCluster}
import backtype.storm.generated.StormTopology
import scala.concurrent.duration._

/**
 * Provides convenience functions to run Storm topologies locally and remotely (i.e. in a "real" Storm cluster).
 */
object StormRunner {

  def runTopologyLocally(topology: StormTopology, topologyName: String, conf: Config, runtime: Duration) {
    val cluster: LocalCluster = new LocalCluster
    cluster.submitTopology(topologyName, conf, topology)
    Thread.sleep(runtime.toMillis)
    cluster.killTopology(topologyName)
    cluster.shutdown()
  }

  def runTopologyRemotely(topology: StormTopology, topologyName: String, conf: Config) {
    StormSubmitter.submitTopology(topologyName, conf, topology)
  }

}
