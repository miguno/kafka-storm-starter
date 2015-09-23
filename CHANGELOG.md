# 0.2.0 (unreleased)

BACKWARDS INCOMPATIBILITIES

* Use Java 7 instead of Java 6.

IMPROVEMENTS

* Use Storm 0.9.5.  This includes two notable improvements:
    * We can and do use the Kafka 0.8 compatible Kafka spout included in Storm 0.9.2+.
    * We use ZooKeeper 3.4.5, up from 3.3.x before.
* Use Spark 1.1.1.
* Add Spark Streaming example, which reads from Kafka and writes to Kafka.  The streaming job is fucntionally equivalent
  to the test topologies in `KafkaStormSpec`.
* AvroKafkaSinkBolt should not declare any output fields because it writes to Kafka only, it does not emit any tuples.
* Reduce logging output when running tests to minimize distraction and confusion.
* Disable ZooKeeper reconnection attempts in the test topology of `KafkaStormSpec` to prevent the Kafka spout from
  logging ZooKeeper connection errors.
* Improve and consolidate the setup we use to lauch in-memory Kafka and ZooKeeper clusters during testing.
* Add startup() and shutdown() methods to Kafka consumer apps to improve lifecycle management.
* Kafka producer apps have more granular control to which topics messages are being sent.

BUG FIXES

* Do not provide multiple logging bindings during run (thanks viktortnk) [GH-3]


# 0.1.0 (May 27, 2014)

* Initial release.  Integrates Kafka 0.8.1.1 with Storm 0.9.1-incubating.
