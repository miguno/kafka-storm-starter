package com.miguno.kafkastorm.integration

import _root_.kafka.utils.Logging
import backtype.storm.{Config, ILocalCluster, Testing}
import backtype.storm.testing._
import backtype.storm.topology.TopologyBuilder
import backtype.storm.tuple.{Fields, Values}
import org.scalatest._

/**
 * For more details on Storm unit testing please take a look at:
 * https://github.com/xumingming/storm-lib/blob/master/src/jvm/storm/TestingApiDemo.java
 */
@DoNotDiscover
class StormSpec extends FunSpec with Matchers with BeforeAndAfterAll with GivenWhenThen with Logging {

  describe("Storm") {

    it("should start a local cluster", IntegrationTest) {
      Given("no cluster")

      When("I start a LocalCluster instance")
      val mkClusterParam = new MkClusterParam
      mkClusterParam.setSupervisors(2)
      mkClusterParam.setPortsPerSupervisor(2)
      val daemonConf = new Config
      daemonConf.put(Config.SUPERVISOR_ENABLE, false: java.lang.Boolean)
      daemonConf.put(Config.TOPOLOGY_ACKER_EXECUTORS, 0: Integer)
      mkClusterParam.setDaemonConf(daemonConf)

      // When testing your topology, you need a `LocalCluster` to run your topologies.  Normally this would mean you'd
      // have to perform lifecycle management of that local cluster, i.e. you'd need to create it, and after using it,
      // you'd need to stop it.  Using `Testing.withLocalCluster` you don't need to do any of this, just use the
      // `cluster` provided through the param of `TestJob.run`.`
      Testing.withLocalCluster(mkClusterParam, new TestJob {
        override def run(stormCluster: ILocalCluster) {
          Then("the local cluster should start properly")
          stormCluster.getState shouldNot be(null)
        }
      })
    }

    it("should run a basic topology", IntegrationTest) {
      Given("a local cluster")
      And("a wordcount topology")
      val mkClusterParam = new MkClusterParam
      mkClusterParam.setSupervisors(4)
      val daemonConf = new Config
      daemonConf.put(Config.STORM_LOCAL_MODE_ZMQ, false: java.lang.Boolean)
      mkClusterParam.setDaemonConf(daemonConf)

      // Base topology setup
      val builder = new TopologyBuilder
      val spoutId = "wordSpout"
      builder.setSpout(spoutId, new TestWordSpout(true), 3)
      val wordCounterId = "wordCounterBolt"
      builder.setBolt(wordCounterId, new TestWordCounter, 4).fieldsGrouping(spoutId, new Fields("word"))
      val globalCountId = "globalCountBolt"
      builder.setBolt(globalCountId, new TestGlobalCount).globalGrouping(spoutId)
      val aggregatesCounterId = "aggregatesCounterBolt"
      builder.setBolt(aggregatesCounterId, new TestAggregatesCounter).globalGrouping(wordCounterId)
      val topology = builder.createTopology()
      val completeTopologyParam = new CompleteTopologyParam

      And("the input words alice, bob, joe, alice")
      val mockedSources = new MockedSources()
      mockedSources.addMockData(spoutId, new Values("alice"), new Values("bob"), new Values("joe"), new Values("alice"))
      completeTopologyParam.setMockedSources(mockedSources)

      // Finalize topology config
      val conf = new Config
      conf.setNumWorkers(2)
      completeTopologyParam.setStormConf(conf)

      When("I submit the topology")
      var result: Option[java.util.Map[_, _]] = None
      Testing.withSimulatedTimeLocalCluster(mkClusterParam, new TestJob() {
        override def run(stormCluster: ILocalCluster) {
          // `completeTopology()` takes your topology, cluster, and configuration.  It will mock out the spouts you
          // specify, and will run the topology until it is idle and all tuples from the spouts have been either acked or
          // failed, and return all the tuples that have been emitted from all the topology components.
          result = Some(Testing.completeTopology(stormCluster, topology, completeTopologyParam))
        }
      })

      // We could split this `Then()` into multiple ones, each of which covering one of the `Testing.multiseteq()` calls
      // below.  Left as an exercise for the reader. :-)
      Then("the topology should properly count the words")
      // Type ascription required for Scala-Java interoperability.
      val one = 1: Integer
      val two = 2: Integer
      val three = 3: Integer
      val four = 4: Integer

      // Verify the expected behavior for each of the components (spout + bolts) in the topology by comparing
      // their actual output tuples vs. the corresponding expected output tuples.
      for {
        r <- result
      } {
        Testing.multiseteq(Testing.readTuples(r, spoutId),
          new Values(new Values("alice"), new Values("bob"), new Values("joe"), new Values("alice"))) should be(true)
        Testing.multiseteq(Testing.readTuples(r, wordCounterId),
          new Values(new Values("alice", one), new Values("alice", two), new Values("bob", one), new Values("joe", one))) should be(true)
        Testing.multiseteq(Testing.readTuples(r, globalCountId),
          new Values(new Values(one), new Values(two), new Values(three), new Values(four))) should be(true)
        Testing.multiseteq(Testing.readTuples(r, aggregatesCounterId),
          new Values(new Values(one), new Values(two), new Values(three), new Values(four))) should be(true)
      }
    }

  }

}