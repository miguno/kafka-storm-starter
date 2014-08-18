# 0.2.0 (unreleased)

IMPROVEMENTS

* Use Storm 0.9.2.  This includes two notable improvements:
    * We can and do use the Kafka 0.8 compatible Kafka spout included in Storm 0.9.2.
    * We use ZooKeeper 3.4.5, up from 3.3.x before.
* AvroKafkaSinkBolt should not declare any output fields because it writes to Kafka only, it does not emit any tuples.
* Reduce logging output when running tests to minimize distraction and confusion.

BUG FIXES

* Do not provide multiple logging bindings during run (thanks viktortnk) [GH-3]


# 0.1.0 (May 27, 2014)

* Initial release.  Integrates Kafka 0.8.1.1 with Storm 0.9.1-incubating.
