package com.miguno.kafkastorm.integration

import com.miguno.kafkastorm.spark.KafkaSparkStreamingSpec
import org.scalatest.Stepwise

class IntegrationSuite extends Stepwise(
  new KafkaSpec,
  new StormSpec,
  new KafkaStormSpec,
  new KafkaSparkStreamingSpec
)