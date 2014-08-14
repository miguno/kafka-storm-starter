package com.miguno.kafkastorm.storm

import com.miguno.avro.Tweet
import com.twitter.bijection.Injection
import com.twitter.bijection.avro.SpecificAvroCodecs
import org.scalatest.{FunSpec, GivenWhenThen, Matchers}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.reflectiveCalls

class AvroSchemeSpec extends FunSpec with Matchers with GivenWhenThen {

  implicit val specificAvroBinaryInjectionForTweet = SpecificAvroCodecs.toBinary[Tweet]

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

  describe("An AvroScheme") {

    it("should have a single output field named 'pojo'") {
      Given("a scheme")
      val scheme = new AvroScheme

      When("I get its output fields")
      val outputFields = scheme.getOutputFields()

      Then("there should only be a single field")
      outputFields.size() should be(1)
      And("this field should be named 'pojo'")
      outputFields.contains("pojo") should be(true)
    }


    it("should deserialize binary records of the configured type into pojos") {
      Given("a scheme for type Tweet ")
      val scheme = new AvroScheme[Tweet]
      And("some binary-encoded Tweet records")
      val tweets = fixture.messages
      val encodedTweets = tweets.map(Injection(_))

      When("I deserialize the binary records into pojos")
      val actualTweets = for {
        l <- encodedTweets.map(scheme.deserialize)
        tweet <- l.asScala
      } yield tweet

      Then("the pojos should be equal to the original pojos")
      actualTweets should be(tweets)
    }

    it("should throw a runtime exception when serialization fails") {
      Given("a scheme for type Tweet ")
      val scheme = new AvroScheme[Tweet]
      And("an invalid binary record")
      val invalidBytes = Array[Byte](1, 2, 3, 4)

      When("I deserialize the record into a pojo")

      Then("the scheme should throw a runtime exception")
      val exception = intercept[RuntimeException] {
        scheme.deserialize(invalidBytes)
      }
      And("the exception should provide a meaningful explanation")
      exception.getMessage should be("Could not decode input bytes")
    }

  }

  describe("An AvroScheme companion object") {

    it("should create an AvroScheme for the correct type") {
      Given("a companion object")

      When("I ask it to create a scheme for type Tweet")
      val scheme = AvroScheme.ofType(classOf[Tweet])

      Then("the scheme should be an AvroScheme")
      scheme shouldBe an[AvroScheme[_]]
      And("the scheme should be parameterized with the type Tweet")
      scheme.tpe.shouldEqual(manifest[Tweet])
    }

  }

}