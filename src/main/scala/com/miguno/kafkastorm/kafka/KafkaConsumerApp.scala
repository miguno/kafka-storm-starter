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
 * @param numStreams The number of Kafka streams to create for consuming the topic.  Currently, each stream will be
 *                   consumed by its own dedicated thread (1:1 mapping), but this behavior is not guaranteed in the
 *                   future.
 * @param config Additional consumer configuration settings.
 */
class KafkaConsumerApp[T](val topic: String,
                          val zookeeperConnect: String,
                          val numStreams: Int,
                          config: Properties = new Properties) extends LazyLogging {

  private val effectiveConfig = {
    val c = new Properties
    c.load(this.getClass.getResourceAsStream("/consumer-defaults.properties"))
    c.putAll(config)
    c.put("zookeeper.connect", zookeeperConnect)
    c
  }

  private val executor = Executors.newFixedThreadPool(numStreams)
  private val consumerConnector = Consumer.create(new ConsumerConfig(effectiveConfig))

  logger.info(s"Connecting to topic $topic via ZooKeeper $zookeeperConnect")

  def startConsumers(f: (MessageAndMetadata[Array[Byte], Array[Byte]], ConsumerTaskContext, Option[T]) => Unit,
                     startup: (ConsumerTaskContext) => Option[T] = (c: ConsumerTaskContext) => None,
                     shutdown: (ConsumerTaskContext, Option[T]) => Unit = (c: ConsumerTaskContext, t: Option[T]) => ()) {
    val topicCountMap = Map(topic -> numStreams)
    val valueDecoder = new DefaultDecoder
    val keyDecoder = valueDecoder
    val consumerMap = consumerConnector.createMessageStreams(topicCountMap, keyDecoder, valueDecoder)
    val consumerThreads = consumerMap.get(topic) match {
      case Some(streams) => streams.view.zipWithIndex map {
        case (stream, threadId) =>
          new ConsumerTask(stream, new ConsumerTaskContext(threadId, effectiveConfig), f, startup, shutdown)
      }
      case _ => Seq()
    }
    consumerThreads foreach executor.submit
  }

  def shutdown() {
    logger.debug("Shutting down Kafka consumer connector")
    consumerConnector.shutdown()
    logger.debug("Shutting down thread pool of consumer tasks")
    executor.shutdown()
  }

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run() {
      shutdown()
    }
  })

}

class ConsumerTask[K, V, T, C <: ConsumerTaskContext](stream: KafkaStream[K, V],
                                                      context: C,
                                                      f: (MessageAndMetadata[K, V], C, Option[T]) => Unit,
                                                      startup: (C) => Option[T],
                                                      shutdown: (C, Option[T]) => Unit)
  extends Runnable with LazyLogging {

  private var t: Option[T] = _

  @volatile private var shutdownAlreadyRanOnce = false

  override def run() {
    logger.debug(s"Consumer task of thread ${context.threadId} entered run()")
    t = startup(context)
    try {
      stream foreach {
        case msg: MessageAndMetadata[_, _] =>
          logger.trace(s"Thread ${context.threadId} received message: " + msg)
          f(msg, context, t)
        case _ => logger.trace(s"Received unexpected message type from broker")
      }
      gracefulShutdown()
    }
    catch {
      case e: InterruptedException => logger.debug(s"Consumer task of thread ${context.threadId} was interrupted")
    }
  }

  def gracefulShutdown() {
    if (!shutdownAlreadyRanOnce) {
      logger.debug("Performing graceful shutdown")
      shutdownAlreadyRanOnce = true
      shutdown(context, t)
    }
    else logger.debug("Graceful shutdown requested but it already ran once, so it will not be run again.")
  }

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run() {
      logger.debug("Shutdown hook triggered!")
      gracefulShutdown()
    }
  })

}

case class ConsumerTaskContext(threadId: Int, config: Properties)