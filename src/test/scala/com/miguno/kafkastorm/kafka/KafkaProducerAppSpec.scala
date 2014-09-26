package com.miguno.kafkastorm.kafka

import java.util.Properties

import com.miguno.kafkastorm.logging.LazyLogging
import kafka.producer.{KeyedMessage, Producer}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, GivenWhenThen, Matchers}

class KafkaProducerAppSpec extends FunSpec with Matchers with GivenWhenThen with MockitoSugar with LazyLogging {

  private val AnyTopic = "some-default-topic"
  private val AnyBrokerList = "a:9092,b:9093"
  private val AnyConfigParam = "queue.buffering.max.ms"
  private val AnyConfigValue = "12345"
  private val AnyKey = Array[Byte]()
  private val AnyValue = Array[Byte]()

  describe("A KafkaProducerApp") {

    it("should let the user configure the broker list") {
      Given("no app")

      When("I create an app with the broker list set to " + AnyBrokerList)
      val producerApp = new KafkaProducerApp(AnyBrokerList, defaultTopic = Option(AnyTopic))

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
      val producerApp = new KafkaProducerApp(AnyBrokerList, defaultTopic = Option(AnyTopic))

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
      val producerApp = new KafkaProducerApp(AnyBrokerList, config, Option(AnyTopic))

      Then(s"the Kafka producer's $AnyConfigParam parameter should be to set to $AnyConfigValue")
      producerApp.config.props.getString(AnyConfigParam) should be(AnyConfigValue)
    }

    it("should send to the default topic if no specific topic is requested") {
      Given("a default topic")
      val defaultTopic = AnyTopic
      And("an app that is configured with this default topic")
      val producer = mock[Producer[Array[Byte], Array[Byte]]]
      val producerApp = spy(new KafkaProducerApp(AnyBrokerList, defaultTopic = Option(defaultTopic),
        producer = Option(producer)))

      When("I send a message to Kafka without specifying a particular topic")
      producerApp.send(AnyKey, AnyValue)

      Then("the message should be sent to the default topic")
      val msg = new KeyedMessage(defaultTopic, AnyKey, AnyValue)
      verify(producer).send(msg)
    }

  }

}