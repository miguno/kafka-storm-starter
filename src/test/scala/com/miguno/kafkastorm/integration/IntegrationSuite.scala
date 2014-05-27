package com.miguno.kafkastorm.integration

import org.scalatest.Stepwise

class IntegrationSuite extends Stepwise(
  new KafkaSpec,
  new StormSpec,
  new KafkaStormSpec
)