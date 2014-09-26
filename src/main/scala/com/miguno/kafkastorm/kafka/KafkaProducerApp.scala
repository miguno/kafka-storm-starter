package com.miguno.kafkastorm.kafka

import java.util.Properties

import kafka.producer.{KeyedMessage, Producer, ProducerConfig}

/**
 * Demonstrates how to implement a simple Kafka producer application to send data to Kafka.
 *
 * Don't read too much into the actual implementation of this class.  Its sole purpose is to showcase the use of the
 * Kafka API.
 *
 * @param brokerList  Value for Kafka's `metadata.broker.list` setting.
 * @param producerConfig Additional producer configuration settings.
 * @param defaultTopic The default Kafka topic to send data to;  the default topic is used as a fallback when you do not
 *                     provide a specific topic when calling `send()`.
 * @param producer An existing [[kafka.producer.Producer]] instance to use for sending data to Kafka.  Primarily used
 *                 for testing.  If `producer` is set, then we ignore the `brokerList` and `producerConfig` parameters.
 */
case class KafkaProducerApp(brokerList: String,
                            producerConfig: Properties = new Properties,
                            defaultTopic: Option[String] = None,
                            producer: Option[Producer[Array[Byte], Array[Byte]]] = None) {

  type Key = Array[Byte]
  type Val = Array[Byte]

  require(brokerList == null || !brokerList.isEmpty, "Must set broker list")

  private val p = producer getOrElse {
    val effectiveConfig = {
      val c = new Properties
      c.load(this.getClass.getResourceAsStream("/producer-defaults.properties"))
      c.putAll(producerConfig)
      c.put("metadata.broker.list", brokerList)
      c
    }
    new Producer[Array[Byte], Array[Byte]](new ProducerConfig(effectiveConfig))
  }

  // The configuration field of the wrapped producer is immutable (including its nested fields), so it's safe to expose
  // it directly.
  val config = p.config

  private def toMessage(value: Val, key: Option[Key] = None, topic: Option[String] = None): KeyedMessage[Key, Val] = {
    val t = topic.getOrElse(defaultTopic.getOrElse(throw new IllegalArgumentException("Must provide topic or default topic")))
    require(!t.isEmpty, "Topic must not be empty")
    key match {
      case Some(key) => new KeyedMessage(t, key, value)
      case _ => new KeyedMessage(t, value)
    }
  }

  def send(key: Key, value: Val, topic: Option[String] = None) {
    p.send(toMessage(value, Option(key), topic))
  }

  def send(value: Val, topic: Option[String]) {
    send(null, value, topic)
  }

  def send(value: Val, topic: String) {
    send(null, value, Option(topic))
  }

  def send(value: Val) {
    send(null, value, None)
  }

  def shutdown(): Unit = p.close()

}

/**
 * Creates KafkaProducerApp instances.
 *
 * We require such a factory because of how Storm and notably
 * [[http://storm.incubator.apache.org/documentation/Serialization.html serialization within Storm]] work.
 * Without such a factory we cannot properly unit tests Storm bolts that need to write to Kafka.
 *
 * Preferably we would simply pass a Kafka producer directly to a Storm bolt.  During testing we could then mock this
 * collaborator.  However this intuitive approach fails at (Storm) runtime because Kafka producers are not serializable.
 * The alternative approach of instantiating the Kafka producer from within the bolt (e.g. using a `@transient lazy val`
 * field) does work at runtime but prevents us from verifying the correct interaction between our bolt's code and its
 * collaborator, the Kafka producer, because we cannot easily mock the producer in this setup.  The chosen approach of
 * the factory method, while introducing some level of unwanted indirection and complexity, is a pragmatic approach to
 * make our Storm code work correctly at runtime and to make it testable.
 *
 * @param topic The Kafka topic to send data to.
 * @param brokerList  Value for Kafka's `metadata.broker.list` setting.
 * @param config Additional producer configuration settings.
 */
abstract class KafkaProducerAppFactory(brokerList: String, config: Properties, topic: Option[String] = None)
  extends Serializable {

  def newInstance(): KafkaProducerApp
}

class BaseKafkaProducerAppFactory(brokerList: String,
                                  config: Properties = new Properties,
                                  defaultTopic: Option[String] = None)
  extends KafkaProducerAppFactory(brokerList, config, defaultTopic) {

  override def newInstance() = new KafkaProducerApp(brokerList, config, defaultTopic)

}