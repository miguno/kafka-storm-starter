package com.miguno.kafkastorm.kafka

import java.util.Properties
import java.util.concurrent.Executors

import com.miguno.kafkastorm.logging.LazyLogging
import kafka.consumer.{Consumer, ConsumerConfig, KafkaStream}
import kafka.message.MessageAndMetadata
import kafka.serializer.DefaultDecoder

/**
 * Demonstrates how to implement a simple Kafka consumer application to read data from Kafka.
 *
 * Don't read too much into the actual implementation of this class.  Its sole purpose is to showcase the use of the
 * Kafka API.
 *
 * @param topic The Kafka topic to read data from.
 * @param zookeeperConnect The ZooKeeper connection string aka `zookeeper.connect` in `hostnameOrIp:port` format.
 *                         Example: `127.0.0.1:2181`.
 * @param numThreads The number of threads used by the consumer application to read from Kafka.
 * @param config Additional consumer configuration settings.
 */
class KafkaConsumerApp(
                     val topic: String,
                     val zookeeperConnect: String,
                     val numThreads: Int,
                     config: Properties = new Properties
                     ) extends LazyLogging {

  private val effectiveConfig = {
    val c = new Properties
    c.load(this.getClass.getResourceAsStream("/consumer-defaults.properties"))
    c.putAll(config)
    c.put("zookeeper.connect", zookeeperConnect)
    c
  }

  private val executor = Executors.newFixedThreadPool(numThreads)
  private val consumerConnector = Consumer.create(new ConsumerConfig(effectiveConfig))

  logger.info(s"Connecting to topic $topic via ZooKeeper $zookeeperConnect")

  def startConsumers(f: (MessageAndMetadata[Array[Byte], Array[Byte]], ConsumerTaskContext) => Unit) {
    val topicCountMap = Map(topic -> numThreads)
    val valueDecoder = new DefaultDecoder
    val keyDecoder = valueDecoder
    val consumerMap = consumerConnector.createMessageStreams(topicCountMap, keyDecoder, valueDecoder)
    val consumerThreads = consumerMap.get(topic) match {
      case Some(streams) => streams.view.zipWithIndex map {
        case (stream, threadId) =>
          new ConsumerTask(stream, new ConsumerTaskContext(threadId), f)
      }
      case _ => Seq()
    }
    consumerThreads foreach executor.submit
  }

  def shutdown() {
    consumerConnector.shutdown()
    executor.shutdown()
  }

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run() {
      shutdown()
    }
  })

}

class ConsumerTask[K, V, C <: ConsumerTaskContext](stream: KafkaStream[K, V], context: C,
                                                   f: (MessageAndMetadata[K, V], C) => Unit)
  extends Runnable with LazyLogging {

  override def run() {
    logger.info(s"Consumer thread ${context.threadId} started")
    stream foreach {
      case msg: MessageAndMetadata[_, _] =>
        logger.trace(s"Thread ${context.threadId} received message: " + msg)
        f(msg, context)
      case _ => logger.trace(s"Received unexpected message type from broker")
    }
    logger.info(s"Shutting down consumer thread ${context.threadId}")
  }

}

case class ConsumerTaskContext(threadId: Int)