package com.miguno.kafkastorm.storm

import backtype.storm.topology.{BasicOutputCollector, OutputFieldsDeclarer}
import backtype.storm.tuple.{Fields, Tuple, Values}
import com.miguno.avro.Tweet
import com.twitter.bijection.Injection
import com.twitter.bijection.avro.SpecificAvroCodecs
import org.mockito.Matchers._
import org.mockito.Mockito.{when => mwhen, _}
import org.scalatest.{FunSpec, GivenWhenThen, Matchers}
import org.scalatest.mock.MockitoSugar
import scala.concurrent.duration._

class AvroDecoderBoltSpec extends FunSpec with Matchers with GivenWhenThen with MockitoSugar {

  implicit val specificAvroBinaryInjection: Injection[Tweet, Array[Byte]] = SpecificAvroCodecs.toBinary[Tweet]

  private type AnyAvroSpecificRecordBase = Tweet

  private val AnyTweet = new Tweet("ANY_USER_1", "ANY_TEXT_1", 1234.seconds.toSeconds)
  private val AnyTweetInAvroBytes = Injection[Tweet, Array[Byte]](AnyTweet)

  describe("An AvroDecoderBolt") {

    it("should read by default the input field 'bytes' from incoming tuples") {
      Given("no bolt")

      When("I create a bolt without customizing the input field name")
      val bolt = new AvroDecoderBolt[AnyAvroSpecificRecordBase]
      And("the bolt receives a tuple")
      val tuple = mock[Tuple]
      val collector = mock[BasicOutputCollector]
      bolt.execute(tuple, collector)

      Then("the bolt should read the field 'bytes' from the tuple")
      verify(tuple, times(1)).getBinaryByField("bytes")
      () // prevent scalac warning about discarded non-Unit value
    }

    it("should let the user configure the name of the input field to read from incoming tuples") {
      Given("no bolt")

      When("I create a bolt with a custom input field name 'foobar'")
      val bolt = new AvroDecoderBolt[AnyAvroSpecificRecordBase](inputField = "foobar")
      And("the bolt receives a tuple")
      val tuple = mock[Tuple]
      val collector = mock[BasicOutputCollector]
      bolt.execute(tuple, collector)

      Then("the bolt should read the field 'foobar' from the tuple")
      verify(tuple, times(1)).getBinaryByField("foobar")
      () // prevent scalac warning about discarded non-Unit value
    }

    it("should deserialize binary records into pojos and send the pojos to downstream bolts") {
      Given("a bolt of type Tweet")
      val bolt = new AvroDecoderBolt[Tweet]
      And("a Tweet record")
      val tuple = mock[Tuple]
      mwhen(tuple.getBinaryByField(anyString)).thenReturn(AnyTweetInAvroBytes)

      When("the bolt receives the Tweet record")
      val collector = mock[BasicOutputCollector]
      bolt.execute(tuple, collector)

      Then("the bolt should send the decoded Tweet pojo to downstream bolts")
      verify(collector, times(1)).emit(new Values(AnyTweet))
      () // prevent scalac warning about discarded non-Unit value
    }

    it("should skip over tuples that contain invalid binary records") {
      Given("a bolt of type Tweet")
      val bolt = new AvroDecoderBolt[Tweet]
      And("an invalid binary record")
      val tuple = mock[Tuple]
      val invalidBinaryRecord = Array[Byte](1, 2, 3, 4)
      mwhen(tuple.getBinaryByField(anyString)).thenReturn(invalidBinaryRecord)

      When("the bolt receives the record")
      val collector = mock[BasicOutputCollector]
      bolt.execute(tuple, collector)

      Then("the bolt should not send any data to downstream bolts")
      verifyZeroInteractions(collector)
    }

    it("should skip over tuples for which reading fails") {
      Given("a bolt")
      val bolt = new AvroDecoderBolt[AnyAvroSpecificRecordBase]
      And("a tuple from which one cannot read")
      val tuple = mock[Tuple]
      mwhen(tuple.getBinaryByField(anyString)).thenReturn(null)

      When("the bolt receives the tuple")
      val collector = mock[BasicOutputCollector]
      bolt.execute(tuple, collector)

      Then("the bolt should not send any data to downstream bolts")
      verifyZeroInteractions(collector)
    }

    it("should declare a single output field with the default name 'pojo'") {
      Given("no bolt")

      When("I create a bolt without customizing the output field name")
      val bolt = new AvroDecoderBolt[Tweet]

      Then("the bolt should declare a single output field named 'pojo'")
      val declarer = mock[OutputFieldsDeclarer]
      bolt.declareOutputFields(declarer)
      // We use ArgumentMatcher as a workaround because Storm's Field class does not implement a proper `equals()`
      // method, and Mockito relies on `equals()` for verification.  Because of that the following typical approach
      // does NOT work: `verify(declarer, times(1)).declare(new Fields("pojo"))`.
      verify(declarer, times(1)).declare(argThat(FieldsEqualTo(new Fields("pojo"))))
    }

    it("should let the user define the name of its output field") {
      Given("no bolt")

      When("I create a bolt with a custom output field name")
      val bolt = new AvroDecoderBolt[Tweet](outputField = "myCustomFieldName")

      Then("the bolt should declare a single output field with this custom name")
      val declarer = mock[OutputFieldsDeclarer]
      bolt.declareOutputFields(declarer)
      verify(declarer, times(1)).declare(argThat(FieldsEqualTo(new Fields("myCustomFieldName"))))
    }

  }

  describe("An AvroDecoderBolt companion object") {

    it("should create an AvroDecoderBolt for the correct type") {
      Given("a companion object")

      When("I ask it to create a bolt for type Tweet")
      val bolt = AvroDecoderBolt.ofType(classOf[Tweet])

      Then("the bolt should be an AvroDecoderBolt")
      bolt shouldBe an[AvroDecoderBolt[_]]
      And("the bolt should be parameterized with the type Tweet")
      bolt.tpe.shouldEqual(manifest[Tweet])
    }

  }

}