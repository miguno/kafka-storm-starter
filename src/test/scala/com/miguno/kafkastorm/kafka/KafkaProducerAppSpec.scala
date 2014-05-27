package com.miguno.kafkastorm.kafka

import _root_.kafka.utils.Logging
import java.util.Properties
import org.scalatest.{FunSpec, GivenWhenThen, Matchers}

class KafkaProducerAppSpec extends FunSpec with Matchers with GivenWhenThen with Logging {

  private val AnyTopic = "some-topic"
  private val AnyBrokerList = "a:9092,b:9093"
  private val AnyConfigParam = "queue.buffering.max.ms"
  private val AnyConfigValue = "12345"

  describe("A KafkaProducerApp") {

    it("should let the user configure the broker list") {
      Given("no app")

      When("I create an app with the broker list set to " + AnyBrokerList)
      val producerApp = new KafkaProducerApp(AnyTopic, AnyBrokerList)

      Then("the Kafka producer's metadata.broker.list config parameter should be set to this value")
      producerApp.config.props.getString("metadata.broker.list") should be(AnyBrokerList)
    }

    it("should use the broker list constructor parameter as the authoritative setting for the broker list") {
      Given("no app")

      When("I create an app with a producer config that sets the broker list to notMe:1234")
      val config = {
        val c = new Properties
        c.put("metadata.broker.list", "notMe:1234")
        c
      }
      And("with the constructor parameter that sets the broker list to " + AnyBrokerList)
      val producerApp = new KafkaProducerApp(AnyTopic, AnyBrokerList, config)

      Then("the Kafka producer's actual broker list should be " + AnyBrokerList)
      producerApp.config.props.getString("metadata.broker.list") should be(AnyBrokerList)
    }

    it("should let the user customize the Kafka producer configuration") {
      Given("no app")

      When(s"I create an app with a producer config that sets $AnyConfigParam to $AnyConfigValue")
      val config = {
        val c = new Properties
        c.put(AnyConfigParam, AnyConfigValue)
        c
      }
      val producerApp = new KafkaProducerApp(AnyTopic, AnyBrokerList, config)

      Then(s"the Kafka producer's $AnyConfigParam parameter should be to set to $AnyConfigValue")
      producerApp.config.props.getString(AnyConfigParam) should be(AnyConfigValue)
    }

  }

}