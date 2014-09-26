package com.miguno.kafkastorm.integration

import java.util.Properties

import _root_.kafka.message.MessageAndMetadata
import com.miguno.avro.Tweet
import com.miguno.kafkastorm.kafka.ConsumerTaskContext
import com.miguno.kafkastorm.logging.LazyLogging
import com.miguno.kafkastorm.testing.{EmbeddedKafkaZooKeeperCluster, KafkaTopic}
import com.twitter.bijection.Injection
import com.twitter.bijection.avro.SpecificAvroCodecs
import org.scalatest._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.reflectiveCalls

@DoNotDiscover
class KafkaSpec extends FunSpec with Matchers with BeforeAndAfterEach with GivenWhenThen with LazyLogging {

  implicit val specificAvroBinaryInjectionForTweet = SpecificAvroCodecs.toBinary[Tweet]

  private val topic = KafkaTopic("testing")
  private val kafkaZkCluster = new EmbeddedKafkaZooKeeperCluster(topics = Seq(topic))

  override def beforeEach() {
    kafkaZkCluster.start()
  }

  override def afterEach() {
    kafkaZkCluster.stop()
  }

  val fixture = {
    val BeginningOfEpoch = 0.seconds
    val AnyTimestamp = 1234.seconds
    val now = System.currentTimeMillis().millis

    new {
      val t1 = new Tweet("ANY_USER_1", "ANY_TEXT_1", now.toSeconds)
      val t2 = new Tweet("ANY_USER_2", "ANY_TEXT_2", BeginningOfEpoch.toSeconds)
      val t3 = new Tweet("ANY_USER_3", "ANY_TEXT_3", AnyTimestamp.toSeconds)

      val messages = Seq(t1, t2, t3)
    }
  }

  describe("Kafka") {

    it("should synchronously send and receive a Tweet in Avro format", IntegrationTest) {
      Given("a ZooKeeper instance")
      And("a Kafka broker instance")
      And("some tweets")
      val tweets = fixture.messages
      And("a single-threaded Kafka consumer group")
      // The Kafka consumer group must be running before the first messages are being sent to the topic.
      val actualTweets = new mutable.SynchronizedQueue[Tweet]
      def consume(m: MessageAndMetadata[Array[Byte], Array[Byte]], c: ConsumerTaskContext): Unit = {
        val tweet = Injection.invert[Tweet, Array[Byte]](m.message)
        for {t <- tweet} {
          logger.info(s"Consumer thread ${c.threadId}: received Tweet $t from ${m.topic}:${m.partition}:${m.offset}")
          actualTweets += t
        }
      }
      kafkaZkCluster.createAndStartConsumer(topic.name, consume)

      When("I start a synchronous Kafka producer that sends the tweets in Avro binary format")
      val producerApp = {
        val c = new Properties
        c.put("producer.type", "sync")
        c.put("client.id", "test-sync-producer")
        c.put("request.required.acks", "1")
        kafkaZkCluster.createProducer(topic.name, c).get
      }
      tweets foreach {
        case tweet =>
          val bytes = Injection[Tweet, Array[Byte]](tweet)
          logger.info(s"Synchronously sending Tweet $tweet to topic ${producerApp.defaultTopic}")
          producerApp.send(bytes)
      }

      Then("the consumer app should receive the tweets")
      val waitForConsumerToReadStormOutput = 300.millis
      logger.debug(s"Waiting $waitForConsumerToReadStormOutput for Kafka consumer threads to read messages")
      Thread.sleep(waitForConsumerToReadStormOutput.toMillis)
      logger.debug("Finished waiting for Kafka consumer threads to read messages")
      actualTweets.toSeq should be(tweets)
    }

    it("should asynchronously send and receive a Tweet in Avro format", IntegrationTest) {
      Given("a ZooKeeper instance")
      And("a Kafka broker instance")
      And("some tweets")
      val tweets = fixture.messages
      And("a single-threaded Kafka consumer group")
      // The Kafka consumer group must be running before the first messages are being sent to the topic.
      val actualTweets = new mutable.SynchronizedQueue[Tweet]
      def consume(m: MessageAndMetadata[Array[Byte], Array[Byte]], c: ConsumerTaskContext) {
        val tweet = Injection.invert[Tweet, Array[Byte]](m.message())
        for {t <- tweet} {
          logger.info(s"Consumer thread ${c.threadId}: received Tweet $t from ${m.topic}:${m.partition}:${m.offset}")
          actualTweets += t
        }
      }
      kafkaZkCluster.createAndStartConsumer(topic.name, consume)

      val waitForConsumerStartup = 300.millis
      logger.debug(s"Waiting $waitForConsumerStartup for Kafka consumer threads to launch")
      Thread.sleep(waitForConsumerStartup.toMillis)
      logger.debug("Finished waiting for Kafka consumer threads to launch")

      When("I start an asynchronous Kafka producer that sends the tweets in Avro binary format")
      val producerApp = {
        val asyncConfig = {
          val c = new Properties
          c.put("producer.type", "async")
          c.put("client.id", "test-sync-producer")
          c.put("request.required.acks", "1")
          // We must set `batch.num.messages` and/or `queue.buffering.max.ms` so that the async producer will actually
          // send our (typically few) test messages before the unit test finishes.
          c.put("batch.num.messages", tweets.size.toString)
          c
        }
        kafkaZkCluster.createProducer(topic.name, asyncConfig).get
      }
      tweets foreach {
        case tweet =>
          val bytes = Injection[Tweet, Array[Byte]](tweet)
          logger.info(s"Asynchronously sending Tweet $tweet to topic ${producerApp.defaultTopic}")
          producerApp.send(bytes)
      }

      Then("the consumer app should receive the tweets")
      val waitForConsumerToReadStormOutput = 300.millis
      logger.debug(s"Waiting $waitForConsumerToReadStormOutput for Kafka consumer threads to read messages")
      Thread.sleep(waitForConsumerToReadStormOutput.toMillis)
      logger.debug("Finished waiting for Kafka consumer threads to read messages")
      actualTweets.toSeq should be(tweets)
    }

  }

}