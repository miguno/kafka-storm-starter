package com.miguno.kafkastorm.storm

import backtype.storm.task.TopologyContext
import backtype.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer}
import backtype.storm.tuple.{Fields, Tuple}
import com.miguno.avro.Tweet
import com.miguno.kafkastorm.kafka.{KafkaProducerApp, KafkaProducerAppFactory}
import com.twitter.bijection.Injection
import com.twitter.bijection.avro.SpecificAvroCodecs
import java.util
import org.mockito.AdditionalMatchers
import org.mockito.Matchers.argThat
import org.mockito.Mockito.{when => mwhen, _}
import org.scalatest.{FunSpec, GivenWhenThen, Matchers}
import org.scalatest.mock.MockitoSugar
import scala.concurrent.duration._

class AvroKafkaSinkBoltSpec extends FunSpec with Matchers with GivenWhenThen with MockitoSugar {

  implicit val specificAvroBinaryInjection: Injection[Tweet, Array[Byte]] = SpecificAvroCodecs.toBinary[Tweet]

  private type AnyAvroSpecificRecordBase = Tweet

  private val AnyTweet = new Tweet("ANY_USER_1", "ANY_TEXT_1", 1234.seconds.toSeconds)
  private val AnyTweetInAvroBytes = Injection[Tweet, Array[Byte]](AnyTweet)
  private val DummyStormConf = new util.HashMap[Object, Object]
  private val DummyStormContext = mock[TopologyContext]

  describe("An AvroKafkaSinkBolt") {

    it("should send pojos of the configured type to Kafka in Avro-encoded binary format") {
      Given("a bolt for type Tweet")
      val producerApp = mock[KafkaProducerApp]
      val producerAppFactory = mock[KafkaProducerAppFactory]
      mwhen(producerAppFactory.newInstance()).thenReturn(producerApp)
      val bolt = new AvroKafkaSinkBolt[Tweet](producerAppFactory)
      bolt.prepare(DummyStormConf, DummyStormContext)

      When("it receives a Tweet pojo")
      val tuple = mock[Tuple]
      // The `Nil: _*` is required workaround because of a known Scala-Java interop problem related to Scala's treatment
      // of Java's varargs.  See http://stackoverflow.com/a/13361530/1743580.
      mwhen(tuple.getValueByField("pojo")).thenReturn(AnyTweet, Nil: _*)
      val collector = mock[BasicOutputCollector]
      bolt.execute(tuple, collector)

      Then("it should send the Avro-encoded pojo to Kafka")
      // Note: The simpler Mockito variant of `verify(kafkaProducer).send(AnyTweetInAvroBytes)` is not enough because
      // this variant will not verify whether the Array[Byte] parameter passed to `send()` has the correct value.
      verify(producerApp).send(AdditionalMatchers.aryEq(AnyTweetInAvroBytes))
      And("it should not send any data to downstream bolts")
      verifyZeroInteractions(collector)
    }

    it("should ignore pojos of an unexpected type") {
      Given("a bolt for type Tweet")
      val producerApp = mock[KafkaProducerApp]
      val producerAppFactory = mock[KafkaProducerAppFactory]
      mwhen(producerAppFactory.newInstance()).thenReturn(producerApp)
      val bolt = new AvroKafkaSinkBolt[Tweet](producerAppFactory)
      bolt.prepare(DummyStormConf, DummyStormContext)

      When("receiving a non-Tweet pojo")
      val tuple = mock[Tuple]
      val invalidPojo = "I am not of the expected type!"
      // The `Nil: _*` is required workaround because of a known Scala-Java interop problem related to Scala's treatment
      // of Java's varargs.  See http://stackoverflow.com/a/13361530/1743580.
      mwhen(tuple.getValueByField("pojo")).thenReturn(invalidPojo, Nil: _*)
      val collector = mock[BasicOutputCollector]
      bolt.execute(tuple, collector)

      Then("it should not send any data to Kafka")
      verifyZeroInteractions(producerApp)
      And("it should not send any data to downstream bolts")
      verifyZeroInteractions(collector)
    }

    it("should not declare any output fields") {
      Given("no bolt")

      When("I create a bolt")
      val producerAppFactory = mock[KafkaProducerAppFactory]
      val bolt = new AvroKafkaSinkBolt[AnyAvroSpecificRecordBase](producerAppFactory)

      Then("it should declare zero output fields")
      val declarer = mock[OutputFieldsDeclarer]
      bolt.declareOutputFields(declarer)
      // We use ArgumentMatcher as a workaround because Storm's Field class does not implement a proper `equals()`
      // method, and Mockito relies on `equals()` for verification.  Because of that the following typical approach
      // does NOT work: `verify(declarer, times(1)).declare(new Fields())`.
      verify(declarer, times(1)).declare(argThat(FieldsEqualTo(new Fields())))
    }

  }

  describe("An AvroKafkaSinkBolt companion object") {

    it("should create an AvroKafkaSinkBolt for the correct type") {
      Given("a companion object")

      When("I ask it to create a bolt for type Tweet")
      val producerAppFactory = mock[KafkaProducerAppFactory]
      val bolt = AvroKafkaSinkBolt.ofType(classOf[Tweet])(producerAppFactory)

      Then("the bolt should be an AvroKafkaSinkBolt")
      bolt shouldBe an[AvroKafkaSinkBolt[_]]
      And("the bolt should be parameterized with the type Tweet")
      bolt.tpe.shouldEqual(manifest[Tweet])
    }

  }

}