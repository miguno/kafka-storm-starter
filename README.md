# kafka-storm-starter [![Build Status](https://travis-ci.org/miguno/kafka-storm-starter.png?branch=develop)](https://travis-ci.org/miguno/kafka-storm-starter)

Code examples that show how to integrate
[Apache Kafka](http://kafka.apache.org/) 0.8+ (latest stable) with
[Apache Storm](http://storm.apache.org/) 0.9+ (latest stable),
while using [Apache Avro](http://avro.apache.org/) as the data serialization format.

---

Table of Contents

* <a href="#Quick-start">Quick start</a>
* <a href="#Features">Features</a>
* <a href="#Implementation-details">Implementation details</a>
* <a href="#Development">Development</a>
    * <a href="#Build-requirements">Build requirements</a>
    * <a href="#Building-the-code">Building the code</a>
    * <a href="#Running-the-tests">Running the tests</a>
    * <a href="#Creating-code-coverage-reports">Creating code coverage reports</a>
    * <a href="#Packaging-the-code">Packaging the code</a>
    * <a href="#IDE-support">IDE support</a>
* <a href="#FAQ">FAQ</a>
    * <a href="#FAQ-Kafka">Kafka</a>
    * <a href="#FAQ-Storm">Storm</a>
* <a href="#Known-issues">Known issues and limitations</a>
    * <a href="#Known-issues-them">Upstream code</a>
    * <a href="#Known-issues-us">kafka-storm-starter code</a>
* <a href="#changelog">Change log</a>
* <a href="#Contributing">Contributing</a>
* <a href="#License">License</a>
* <a href="#References">References</a>
    * <a href="#References-Wirbelsturm">Wirbelsturm</a>
    * <a href="#References-Kafka">Kafka</a>
    * <a href="#References-Storm">Storm</a>
    * <a href="#References-Avro">Avro</a>
    * <a href="#References-Kryo">Kryo</a>

---


<a name="Quick-start"></a>

# Quick start

## Show me!

    $ ./sbt test

This command launches our test suite.

Notably it will run end-to-end tests of Kafka, Storm, and Kafka/Storm as well as Kafka/Spark Streaming integration.
See this abridged version of the test output:

```
[...other tests removed...]

[info] KafkaSpec:
[info] Kafka
[info] - should synchronously send and receive a Tweet in Avro format
[info]   + Given a ZooKeeper instance
[info]   + And a Kafka broker instance
[info]   + And some tweets
[info]   + And a single-threaded Kafka consumer group
[info]   + When I start a synchronous Kafka producer that sends the tweets in Avro binary format
[info]   + Then the consumer app should receive the tweets
[info] - should asynchronously send and receive a Tweet in Avro format
[info]   + Given a ZooKeeper instance
[info]   + And a Kafka broker instance
[info]   + And some tweets
[info]   + And a single-threaded Kafka consumer group
[info]   + When I start an asynchronous Kafka producer that sends the tweets in Avro binary format
[info]   + Then the consumer app should receive the tweets
[info] StormSpec:
[info] Storm
[info] - should start a local cluster
[info]   + Given no cluster
[info]   + When I start a LocalCluster instance
[info]   + Then the local cluster should start properly
[info] - should run a basic topology
[info]   + Given a local cluster
[info]   + And a wordcount topology
[info]   + And the input words alice, bob, joe, alice
[info]   + When I submit the topology
[info]   + Then the topology should properly count the words
[info] KafkaStormSpec:
[info] As a user of Storm
[info] I want to read Avro-encoded data from Kafka
[info] so that I can quickly build Kafka<->Storm data flows
[info] Feature: AvroDecoderBolt[T]
[info]   Scenario: User creates a Storm topology that uses AvroDecoderBolt
[info]     Given a ZooKeeper instance
[info]     And a Kafka broker instance
[info]     And a Storm topology that uses AvroDecoderBolt and that reads tweets from topic testing-input} and writes them as-is to topic testing-output
[info]     And some tweets
[info]     And a synchronous Kafka producer app that writes to the topic testing-input
[info]     And a single-threaded Kafka consumer app that reads from topic testing-output and Avro-decodes the incoming data
[info]     And a Storm topology configuration that registers an Avro Kryo decorator for Tweet
[info]     When I run the Storm topology
[info]     And I Avro-encode the tweets and use the Kafka producer app to sent them to Kafka
[info]     Synchronously sending Tweet {"username": "ANY_USER_1", "text": "ANY_TEXT_1", "timestamp": 1411993272} to topic Some(testing-input)
[info]     Synchronously sending Tweet {"username": "ANY_USER_2", "text": "ANY_TEXT_2", "timestamp": 0} to topic Some(testing-input)
[info]     Synchronously sending Tweet {"username": "ANY_USER_3", "text": "ANY_TEXT_3", "timestamp": 1234} to topic Some(testing-input)
[info]     Then the Kafka consumer app should receive the original tweets from the Storm topology
[info] Feature: AvroScheme[T] for Kafka spout
[info]   Scenario: User creates a Storm topology that uses AvroScheme in Kafka spout
[info]     Given a ZooKeeper instance
[info]     And a Kafka broker instance
[info]     And a Storm topology that uses AvroScheme and that reads tweets from topic testing-input and writes them as-is to topic testing-output
[info]     And some tweets
[info]     And a synchronous Kafka producer app that writes to the topic testing-input
[info]     And a single-threaded Kafka consumer app that reads from topic testing-output and Avro-decodes the incoming data
[info]     And a Storm topology configuration that registers an Avro Kryo decorator for Tweet
[info]     When I run the Storm topology
[info]     And I Avro-encode the tweets and use the Kafka producer app to sent them to Kafka
[info]     Synchronously sending Tweet {"username": "ANY_USER_1", "text": "ANY_TEXT_1", "timestamp": 1411993272} to topic Some(testing-input)
[info]     Synchronously sending Tweet {"username": "ANY_USER_2", "text": "ANY_TEXT_2", "timestamp": 0} to topic Some(testing-input)
[info]     Synchronously sending Tweet {"username": "ANY_USER_3", "text": "ANY_TEXT_3", "timestamp": 1234} to topic Some(testing-input)
[info]     Then the Kafka consumer app should receive the original tweets from the Storm topology
[info] KafkaSparkStreamingSpec:
[info] As a user of Spark Streaming
[info] I want to read Avro-encoded data from Kafka
[info] so that I can quickly build Kafka<->Spark Streaming data flows
[info] Feature: Basic functionality
[info]   Scenario: User creates a Spark Streaming job that reads from and writes to Kafka
[info]     Given a ZooKeeper instance
[info]     And a Kafka broker instance
[info]     And some tweets
[info]     And a synchronous Kafka producer app that writes to the topic KafkaTopic(testing-input,1,1,{})
[info]     And a single-threaded Kafka consumer app that reads from topic KafkaTopic(testing-output,1,1,{}) and Avro-decodes the incoming data
[info]     When I Avro-encode the tweets and use the Kafka producer app to sent them to Kafka
[info]     And I run a streaming job that reads tweets from topic KafkaTopic(testing-input,1,1,{}) and writes them as-is to topic KafkaTopic(testing-output,1,1,{})
[info]     Then the Spark Streaming job should consume all tweets from Kafka
[info]     And the job should write back all tweets to Kafka
[info]     And the Kafka consumer app should receive the original tweets from the Spark Streaming job
[info] Run completed in 45 seconds, 787 milliseconds.
[info] Total number of tests run: 27
[info] Suites: completed 9, aborted 0
[info] Tests: succeeded 27, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```


## Show me one more time!

    $ ./sbt run

This command launches [KafkaStormDemo](src/main/scala/com/miguno/kafkastorm/storm/topologies/KafkaStormDemo.scala).
This demo starts in-memory instances of ZooKeeper, Kafka, and Storm.  It then runs a demo Storm topology that connects
to and reads from the Kafka instance.

You will see output similar to the following (some parts removed to improve readability):

```
7031 [Thread-19] INFO  backtype.storm.daemon.worker - Worker 3f7f1a51-5c9e-43a5-b431-e39a7272215e for storm kafka-storm-starter-1-1400839826 on daa60807-d440-4b45-94fc-8dd7798453d2:1027 has finished loading
7033 [Thread-29-kafka-spout] INFO  storm.kafka.DynamicBrokersReader - Read partition info from zookeeper: GlobalPartitionInformation{partitionMap={0=127.0.0.1:9092}}
7050 [Thread-29-kafka-spout] INFO  backtype.storm.daemon.executor - Opened spout kafka-spout:(1)
7051 [Thread-29-kafka-spout] INFO  backtype.storm.daemon.executor - Activating spout kafka-spout:(1)
7051 [Thread-29-kafka-spout] INFO  storm.kafka.ZkCoordinator - Refreshing partition manager connections
7065 [Thread-29-kafka-spout] INFO  storm.kafka.DynamicBrokersReader - Read partition info from zookeeper: GlobalPartitionInformation{partitionMap={0=127.0.0.1:9092}}
7066 [Thread-29-kafka-spout] INFO  storm.kafka.ZkCoordinator - Deleted partition managers: []
7066 [Thread-29-kafka-spout] INFO  storm.kafka.ZkCoordinator - New partition managers: [Partition{host=127.0.0.1:9092, partition=0}]
7083 [Thread-29-kafka-spout] INFO  storm.kafka.PartitionManager - Read partition information from: /kafka-spout/kafka-storm-starter/partition_0  --> null
7100 [Thread-29-kafka-spout] INFO  storm.kafka.PartitionManager - No partition information found, using configuration to determine offset
7105 [Thread-29-kafka-spout] INFO  storm.kafka.PartitionManager - Starting Kafka 127.0.0.1:0 from offset 18
7106 [Thread-29-kafka-spout] INFO  storm.kafka.ZkCoordinator - Finished refreshing
```

At this point Storm is connected to Kafka (more precisely: to the `testing` topic in Kafka).  Not much will happen
afterwards because a) we are not sending any data to the Kafka topic and b) this demo Storm topology only reads from the
Kafka topic but it does nothing to the data that was read.

Note that this example will actually run _two_ in-memory instances of ZooKeeper:  the first (listening at
`127.0.0.1:2181/tcp`) is used by the Kafka instance, the second (listening at `127.0.0.1:2000/tcp`) is automatically
started and used by the in-memory Storm cluster.  This is because, when running in local aka in-memory mode, Storm
versions < 0.9.3 do not allow you to reconfigure or disable its own ZooKeeper instance (see the
[Storm FAQ](#FAQ-Storm) below for further information).

**To stop the demo application you must kill or `Ctrl-C` the process in the terminal.**

You can use [KafkaStormDemo](src/main/scala/com/miguno/kafkastorm/storm/topologies/KafkaStormDemo.scala) as a starting
point to create your own, "real" Storm topologies that read from a "real" Kafka, Storm, and ZooKeeper infrastructure.
An easy way to get started with such an infrastructure is by deploying Kafka, Storm, and ZooKeeper via a tool such as
[Wirbelsturm](https://github.com/miguno/wirbelsturm).


<a name="Features"></a>

# Features

What features do we showcase in kafka-storm-starter?  Note that we focus on showcasing, and not necessarily on
"production ready".

* How to integrate Kafka and Storm as well as Kafka and Spark Streaming
* How to use [Avro](http://avro.apache.org/) with Kafka, Storm, and Spark Streaming.
* Kafka standalone code examples
    * [KafkaProducerApp](src/main/scala/com/miguno/kafkastorm/kafka/KafkaProducerApp.scala):
      A simple Kafka producer app for writing Avro-encoded data into Kafka.
      [KafkaSpec](src/test/scala/com/miguno/kafkastorm/integration/KafkaSpec.scala) puts this producer to use and shows
      how to use Twitter Bijection to Avro-encode the messages being sent to Kafka.
    * [KafkaConsumerApp](src/main/scala/com/miguno/kafkastorm/kafka/KafkaConsumerApp.scala):
      A simple Kafka consumer app for reading Avro-encoded data from Kafka.
      [KafkaSpec](src/test/scala/com/miguno/kafkastorm/integration/KafkaSpec.scala) puts this consumer to use and shows
      how to use Twitter Bijection to Avro-decode the messages being read from Kafka.
* Storm standalone code examples
    * [AvroDecoderBolt[T]](src/main/scala/com/miguno/kafkastorm/storm/AvroDecoderBolt.scala):
      An `AvroDecoderBolt[T <: org.apache.avro.specific.SpecificRecordBase]` that can be parameterized with the type of
      the Avro record `T` it will deserialize its data to (i.e. no need to write another decoder bolt just because the
      bolt needs to handle a different Avro schema).
    * [AvroScheme[T]](src/main/scala/com/miguno/kafkastorm/storm/serialization/AvroScheme.scala):
      An `AvroScheme[T <: org.apache.avro.specific.SpecificRecordBase]` scheme, i.e. a custom
      `backtype.storm.spout.Scheme` to auto-deserialize a spout's incoming data.  The scheme can be parameterized with
      the type of the Avro record `T` it will deserializes its data to (i.e. no need to write another scheme just
      because the scheme needs to handle a different Avro schema).
        * You can opt to configure a spout (such as the Kafka spout) with `AvroScheme` if you want to perform the Avro
          decoding step directly in the spout instead of placing an `AvroDecoderBolt` after the Kafka spout.  You may
          want to profile your topology which of the two approaches works best for your use case.
    * [TweetAvroKryoDecorator](src/main/scala/com/miguno/kafkastorm/storm/serialization/TweetAvroKryoDecorator.scala):
      A custom `backtype.storm.serialization.IKryoDecorator`, i.e. a custom
      [Kryo serializer for Storm](http://storm.apache.org/documentation/Serialization.html).
        * Unfortunately we have not figured out a way to implement a parameterized `AvroKryoDecorator[T]` variant yet.
          (A "straight-forward" approach we tried -- similar to the other parameterized components -- compiled fine but
          failed at runtime when running the tests).  Code contributions are welcome!
* Kafka and Storm integration
    * [AvroKafkaSinkBolt[T]](src/main/scala/com/miguno/kafkastorm/storm/AvroKafkaSinkBolt.scala):
      An `AvroKafkaSinkBolt[T <: org.apache.avro.specific.SpecificRecordBase]` that can be parameterized with the type
      of the Avro record `T` it will serialize its data to before sending the encoded data to Kafka (i.e. no
      need to write another Kafka sink bolt just because the bolt needs to handle a different Avro schema).
    * Storm topologies that read Avro-encoded data from Kafka:
      [KafkaStormDemo](src/main/scala/com/miguno/kafkastorm/storm/topologies/KafkaStormDemo.scala) and
      [KafkaStormSpec](src/test/scala/com/miguno/kafkastorm/integration/KafkaStormSpec.scala)
    * A Storm topology that writes Avro-encoded data to Kafka:
      [KafkaStormSpec](src/test/scala/com/miguno/kafkastorm/integration/KafkaStormSpec.scala)
* Kafka and Spark Streaming integration
    * [KafkaSparkStreamingSpec](src/test/scala/com/miguno/kafkastorm/spark/KafkaSparkStreamingSpec.scala) a streaming
      job that reads input data from Kafka and writes output data to Kafka.  It demonstrates how to read from all
      partitions of a topic in parallel, how to decouple the downstream parallelism from the number of parttions
      (think: use 20 "threads" for processing the Kafka data even though the Kafka topic has only 5 partitions),
      and how to write the output of the streaming job back into Kafka.  The input and output data is in Avro format,
      and we use Twitter Bijection for the serialization work.
      See my blog post on [Integrating Kafka and Spark Streaming](http://www.michael-noll.com/blog/2014/10/01/kafka-spark-streaming-integration-example-tutorial/) for further details.
* Unit testing
    * [AvroDecoderBoltSpec](src/test/scala/com/miguno/kafkastorm/storm/AvroDecoderBoltSpec.scala)
    * [AvroSchemeSpec](src/test/scala/com/miguno/kafkastorm/storm/serialization/AvroSchemeSpec.scala)
    * And more under [src/test/scala](src/test/scala/com/miguno/kafkastorm/).
* Integration testing
    * [KafkaSpec](src/test/scala/com/miguno/kafkastorm/integration/KafkaSpec.scala):
      Tests for Kafka, which launch and run against in-memory instances of Kafka and ZooKeeper.
      See [EmbeddedKafkaZooKeeperCluster](src/test/scala/com/miguno/kafkastorm/testing/EmbeddedKafkaZooKeeperCluster.scala)
      and its constituents [KafkaEmbedded](src/main/scala/com/miguno/kafkastorm/kafka/KafkaEmbedded.scala) and
      [ZooKeeperEmbedded](src/main/scala/com/miguno/kafkastorm/zookeeper/ZooKeeperEmbedded.scala).
    * [StormSpec](src/test/scala/com/miguno/kafkastorm/integration/StormSpec.scala):
      Tests for Storm, which launch and run against in-memory instances of Storm and ZooKeeper.
    * [KafkaStormSpec](src/test/scala/com/miguno/kafkastorm/integration/KafkaStormSpec.scala):
      Tests for integrating Storm and Kafka, which launch and run against in-memory instances of Kafka, Storm, and
      ZooKeeper.
    * [KafkaSparkStreamingSpec](src/test/scala/com/miguno/kafkastorm/spark/KafkaSparkStreamingSpec.scala):
      Tests for integrating Spark Streaming and Kafka, which launch and run against in-memory instances of Kafka,
      Spark Streaming, and ZooKeeper.


<a name="Implementation-details"></a>

# Implementation details

* We use [Twitter Bijection](https://github.com/twitter/bijection) for Avro encoding and decoding.
* We use [Twitter Chill](https://github.com/twitter/chill/) (which in turn uses Bijection) to implement a
  [custom Kryo serializer for Storm](src/main/scala/com/miguno/kafkastorm/storm/serialization/TweetAvroKryoDecorator.scala)
  that handles our Avro-derived Java class `Tweet` from [twitter.avsc](src/main/avro/twitter.avsc).
* Unit and integration tests are implemented with [ScalaTest](http://scalatest.org/).
* We use [ZooKeeper 3.4.5](https://zookeeper.apache.org/).
* We use the [official Kafka spout](https://github.com/apache/storm/tree/master/external/storm-kafka) of the
  Storm project, which is compatible with Kafka 0.8.


<a name="Development"></a>

# Development


<a name="git-setup"></a>

## Git setup: git-flow

This project follows the [git-flow](https://github.com/nvie/gitflow) approach.  This means, for instance, that:

* The branch `develop` is used for integration of the "next release".
* The branch `master` is used for bringing forth production releases.

Follow the [git-flow installation instructions](https://github.com/nvie/gitflow/wiki/Installation) for your
development machine.

See [git-flow](https://github.com/nvie/gitflow) and the introduction article
[Why aren't you using git-flow?](http://jeffkreeftmeijer.com/2010/why-arent-you-using-git-flow/) for details.


<a name="Build-requirements"></a>

## Build requirements

Your development machine requires:

* Oracle JDK or OpenJDK for Java 7 (Oracle JDK preferred).

This project also needs [Scala](http://www.scala-lang.org/) 2.10.4 and [sbt](http://www.scala-sbt.org/) 0.13.2, but
these will be automatically downloaded and made available (locally/sandboxed) to the project as part of the build setup.


<a name="Building-the-code"></a>

## Building the code

    $ ./sbt clean compile

If you want to only (re)generate Java classes from Avro schemas:

    $ ./sbt avro:generate

Generated Java sources are stored under `target/scala-*/src_managed/main/compiled_avro/`.


<a name="Running-the-tests"></a>

## Running the tests

    $ ./sbt clean test

Here are some examples that demonstrate how you can run only a certain subset of tests:

    # Use `-l` to exclude tests by tag:
    # Run all tests WITH THE EXCEPTION of those tagged as integration tests
    $ ./sbt "test-only * -- -l com.miguno.kafkastorm.integration.IntegrationTest"

    # Use `-n` to include tests by tag (and skip all tests that lack the tag):
    # Run ONLY tests tagged as integration tests
    $ ./sbt "test-only * -- -n com.miguno.kafkastorm.integration.IntegrationTest"

    # Run only the tests in suite AvroSchemeSpec:
    $ ./sbt "test-only com.miguno.kafkastorm.storm.serialization.AvroSchemeSpec"

    # You can also combine the examples above, of course.

Test reports in JUnit XML format are written to `target/test-reports/junitxml/*.xml`.  Make sure that your actual build
steps run the `./sbt test` task, otherwise the JUnit XML reports will not be generate (note that `./sbt scoverage:test`
_will not_ generate the JUnit XML reports unfortunately).

Integration with CI servers:

* Jenkins integration:
    * Configure the build job.
    * Go to _Post-build Actions_.
    * Add a post-build action for _Publish JUnit test result report_.
    * In the _Test report XMLs_ field add the pattern `**/target/test-reports/junitxml/*.xml`.
    * Now each build of your job will have a _Test Result_ link.
* TeamCity integration:
    * Edit the build configuration.
    * Select configuration step 3, _Build steps_.
    * Under _Additional Build Features_ add a new build feature.
    * Use the following build feature configuration:
        * Report type: Ant JUnit
        * Monitoring rules: `target/test-reports/junitxml/*.xml`
    * Now each build of your job will have a _Tests_ tab.

Further details are available at:

* How to tag tests in ScalaTest: [Tagging your tests](http://www.scalatest.org/user_guide/tagging_your_tests)
* How to selectively run tests: [Using ScalaTest with sbt](http://www.scalatest.org/user_guide/using_scalatest_with_sbt)
  and [How to Run Tagged Scala Tests with SBT and ScalaTest](http://code.hootsuite.com/tagged-tests-with-sbt/)


<a name="Creating-code-coverage-reports"></a>

## Creating code coverage reports

We are using [sbt-scoverage](https://github.com/scoverage/sbt-scoverage) to create code coverage reports for unit tests.

Run the unit tests via:

    $ ./sbt clean scoverage:test

* An HTML report will be created at `target/scala-2.10/scoverage-report/index.html`.
* XML reports will be created at:
    * `./target/scala-2.10/coverage-report/cobertura.xml`
    * `./target/scala-2.10/scoverage-report/scoverage.xml`

Integration with CI servers:

* Jenkins integration:
    * Configure the build.
    * Go to _Post-build Actions_.
    * Add a post-build action for _Publish Cobertura Coverage Report_.
    * In the _Cobertura xml report pattern_ field add the pattern `**/target/scala-2.10/coverage-report/cobertura.xml`.
    * Now each build of your job will have a _Coverage Report_ link.
* TeamCity integration:
    * Edit the build configuration.
    * Select configuration step 1, _General settings_.
    * In the _Artifact Paths_ field add the path `target/scala-2.10/scoverage-report/** => coberturareport/`.
    * Now each build of your job will have a _Cobertura Coverage Report_ tab.


<a name="Packaging-the-code"></a>

## Packaging the code

To create a normal ("slim") jar:

    $ ./sbt clean package

    >>> Generates `target/scala-2.10/kafka-storm-starter_2.10-0.2.0-SNAPSHOT.jar`

To create a fat jar, which includes any dependencies of kafka-storm-starter:

    $ ./sbt assembly

    >>> Generates `target/scala-2.10/kafka-storm-starter-assembly-0.2.0-SNAPSHOT.jar`

_Note: By default, `assembly` by itself will NOT run any tests.  If you want to run tests before assembly, chain sbt_
_commands in sequence, e.g. `./sbt test assembly`.  See [assembly.sbt](assembly.sbt)` for details why we do this._

To create a scaladoc/javadoc jar:

    $ ./sbt packageDoc

    >>> Generates `target/scala-2.10/kafka-storm-starter_2.10-0.2.0-SNAPSHOT-javadoc.jar`

To create a sources jar:

    $ ./sbt packageSrc

    >>> Generates `target/scala-2.10/kafka-storm-starter_2.10-0.2.0-SNAPSHOT-sources.jar`

To create API docs:

    $ ./sbt doc

    >>> Generates `target/scala-2.10/api/*` (HTML files)


<a name="IDE-support"></a>

## IDE support

### IntelliJ IDEA

kafka-storm-starter integrates the [sbt-idea plugin](https://github.com/mpeltonen/sbt-idea).  Use the following command
to build IDEA project files:

    $ ./sbt gen-idea

You can then open kafka-storm-starter as a project in IDEA via _File > Open..._ and selecting the top-level directory
of kafka-storm-starter.

**Important note:** There is a bug when using the sbt plugins for Avro and for IntelliJ IDEA in combination.  The sbt
plugin for Avro reads the Avro `*.avsc` schemas stored under `src/main/avro` and generates the corresponding Java
classes, which it stores under `target/scala-2.10/src_managed/main/compiled_avro` (in the case of kafka-storm-starter,
a `Tweet.java` class will be generated from the Avro schema [twitter.avsc](src/main/avro/twitter.avsc)).  The latter
path must be added to IDEA's _Source Folders_ setting, which will happen automatically for you.  However the
aforementioned bug will add a second, incorrect path to _Source Folders_, too, which will cause IDEA to complain about
not being able to find the Avro-generated Java classes (here: the `Tweet` class).

Until this bug is fixed upstream you can use the following workaround, which you must perform everytime you run
`./sbt gen-idea`:

1. In IntelliJ IDEA open the project structure for kafka-storm-starter via _File > Project Structure..._.
2. Under _Project settings_ on the left-hand side select _Modules_.
3. Select the _Sources_ tab on the right-hand side.
4. Remove the problematic `target/scala-2.10/src_managed/main/compiled_avro/com` entry from the _Source Folders_ listing
   (the source folders are colored in light-blue).  Note the trailing `.../com`, which comes from
   `com.miguno.avro.Tweet` in the [twitter.avsc](src/main/avro/twitter.avsc) Avro schema.
5. Click Ok.

See also this screenshot (click to enlarge):

[![Fix bug in IntelliJIDEA when using avro Avro](images/IntelliJ-IDEA-Avro-bug_400x216.png?raw=true)](images/IntelliJ-IDEA-Avro-bug.png?raw=true)


### Eclipse

kafka-storm-starter integrates the [sbt-eclipse plugin](https://github.com/typesafehub/sbteclipse).  Use the following
command to build Eclipse project files:

    $ ./sbt eclipse

Then use the _Import Wizard_ in Eclipse to import _Existing Projects into Workspace_.


<a name="FAQ"></a>

# FAQ


<a name="FAQ-Kafka"></a>

## Kafka

### ZooKeeper exceptions "KeeperException: NoNode for /[ZK path]" logged at INFO level

In short you can normally safely ignore those errors -- it's for a reason they are logged at INFO level and not at ERROR
level.

As described in the mailing list thread [Zookeeper exceptions](http://mail-archives.apache.org/mod_mbox/incubator-kafka-users/201204.mbox/%3CCAFbh0Q3BxaAkyBq1_yUHhUkkhxX4RBQZPAA2pkR4U9+m4VY8nA@mail.gmail.com%3E):

"The reason you see those NoNode error code is the following. Every time we want to create a new [ZK] path, say
`/brokers/ids/1`, we try to create it directly.  If this fails because the parent path doesn't exist, we try to create
the parent path first. This will happen recursively.  However, the `NoNode` error should show up only once, not every
time a broker is started (assuming ZK data hasn't been cleaned up)."

A similar answer was given in the thread
[Clean up kafka environment](http://grokbase.com/t/kafka/users/137qgfyga0/clean-up-kafka-environmet):

"These info messages show up when Kafka tries to create new consumer groups.  While trying to create the children of
`/consumers/[group]`, if the parent path doesn't exist, the zookeeper server logs these messages.  Kafka internally
handles these cases correctly by first creating the parent node."


<a name="FAQ-Storm"></a>

## Storm

### Storm `LocalCluster` and ZooKeeper

[LocalCluster](https://github.com/apache/storm/blob/master/storm-core/src/clj/backtype/storm/LocalCluster.clj)
starts an embedded ZooKeeper instance listening at `localhost:2000/tcp`.  If a different process is already bound to
`2000/tcp`, then Storm will increment the embedded ZooKeeper's port until it finds a free port (`2000` -> `2001` ->
`2002`, and so on).  `LocalCluster` then reads the Storm defaults and overrides some of Storm's configuration (see the
`mk-local-storm-cluster` function in
[testing.clj](https://github.com/apache/storm/blob/master/storm-core/src/clj/backtype/storm/testing.clj) and
the `mk-inprocess-zookeeper` function in
[zookeeper.clj](https://github.com/apache/storm/blob/master/storm-core/src/clj/backtype/storm/zookeeper.clj)
for details):

    STORM-CLUSTER-MODE "local"
    STORM-ZOOKEEPER-PORT zk-port
    STORM-ZOOKEEPER-SERVERS ["localhost"]}

where `zk-port` is the final port chosen.

In Storm versions <= 0.9.2 it is not possible to launch a local Storm cluster via `LocalCluster` without its own embedded
ZooKeeper.  Likewise it is not possible to control on which port the embedded ZooKeeper process will listen -- it will
always follow the `2000/tcp` based algorithm above to set the port.

In Storm 0.9.3 and later you can configure `LocalCluster` to use a custom ZooKeeper instance, thanks to
[STORM-213](https://issues.apache.org/jira/browse/STORM-213).


<a name="Known-issues"></a>

# Known issues and limitations

This section lists known issues and limitations a) for the upstream projects such as Storm and Kafka, and b) for our
own code.


<a name="Known-issues-them"></a>

## Upstream code

### ZooKeeper throws InstanceAlreadyExistsException during tests

_Note: We squelch this message during test runs.  See [log4j.properties](src/test/resources/log4.properties)._

You may see the following exception when running the integration tests, which you can safely ignore:

    [2014-03-07 11:56:59,250] WARN Failed to register with JMX (org.apache.zookeeper.server.ZooKeeperServer)
    javax.management.InstanceAlreadyExistsException: org.apache.ZooKeeperService:name0=StandaloneServer_port-1

The root cause is that in-memory ZooKeeper instances have a hardcoded JMX setup.  And because we cannot prevent Storm's
`LocalCluster` to start its own ZooKeeper instance alongside "ours" (see FAQ section above), there will be two ZK
instances trying to use the same JMX setup.  Since the JMX setup is not relevant for our testing the exception can be
safely ignored, albeit we'd prefer to come up with a proper fix, of course.

See also [ZOOKEEPER-1350: Make JMX registration optional in LearnerZooKeeperServer](https://issues.apache.org/jira/browse/ZOOKEEPER-1350),
which will make it possible to disable JMX registration when using Curator's `TestServer` to run an in-memory ZooKeeper
instance (this patch will be included in ZooKeeper 3.5.0, see JIRA ticket above).


### ZooKeeper version 3.3.4 recommended for use with Kafka 0.8

At the time of writing Kafka 0.8 is not officially compatible with ZooKeeper 3.4.x, which is the latest stable version
of ZooKeeper.  Instead the Kafka project
[recommends ZooKeeper 3.3.4](https://kafka.apache.org/documentation.html#zkversion).

So which version of ZooKeeper should you do pick, particularly if you are already running a ZooKeeper cluster for other
parts of your infrastructure (such as an Hadoop cluster)?

**The TL;DR version is:**  Try using ZooKeeper 3.4.5 for both Kafka and Storm, but see the caveats and workarounds
below.  In the worst case use separate ZooKeeper clusters/versions for Storm (3.4.5) and Kafka (3.3.4).  Generally
speaking though, the best 3.3.x version of ZooKeeper is 3.3.6, which is the latest stable 3.3.x version.  This is
because 3.3.6 fixed a number of serious bugs that could lead to data corruption.

_Tip: You can verify the exact ZK version used in kafka-storm-starter by running `./sbt dependency-graph`._

Notes:

* There is an open Kafka JIRA ticket that covers upgrading Kafka to ZK 3.4.5, see
  [KAFKA-854: Upgrade dependencies for 0.8](https://issues.apache.org/jira/browse/KAFKA-854).
* If in a production environment you run into problems when using ZooKeeper 3.4.5 with Storm <= 0.9.1, you can try
  a [workaround using Google jarjar](https://groups.google.com/forum/#!topic/storm-user/TVVF_jqvD_A) in order to
  deploy ZooKeeper 3.4.5 alongside Storm's/Curator's hard dependency on ZooKeeper 3.3.3.
  [Another user reported](http://grokbase.com/t/gg/storm-user/134f2tw5gx/recommended-zookeeper-version-for-storm-0-8-2)
  that he uses ZK 3.4.5 in production and ZK 3.3.3 for local testing by not including ZooKeeper in the uber jar
  and putting the correct ZK version in the CLASSPATH at runtime.
  [STORM-70: Use ZooKeeper 3.4.5](https://issues.apache.org/jira/browse/STORM-70).


<a name="Known-issues-us"></a>

## kafka-storm-starter code

* Some code in kafka-storm-starter does not look like idiomatic Scala code.  While sometimes this may be our own fault,
  there is one area where we cannot easily prevent this from happening:  When the underlying Java APIs (here: the Java
  API of Storm) do not lend themselves to a more Scala-like code style.  You can see this, for instance, in the way we
  wire the spouts and bolts of a topology.  One alternative, of course, would be to create Scala-fied wrappers but this
  seemed inappropriate for this project.
* We are using `Thread.sleep()` in some tests instead of more intelligent approaches.  To prevent transient failures we
  may thus want to improve those tests.  In Kafka's test suites, for instance, tests are using `waitUntilTrue()` to
  detect more reliably when to proceed (or fail/timeout) with the next step.  See the related discussion in the
  [review request 19696 for KAFKA-1317](https://reviews.apache.org/r/19696/#comment71202).
* We noticed that the tests may fail when using Oracle/Sun JDK 1.6.0_24.  Later versions (e.g. 1.6.0_31) work fine.


<a name="changelog"></a>

# Change log

See [CHANGELOG](CHANGELOG.md).


<a name="Contributing"></a>

# Contributing to kafka-storm-starter

Code contributions, bug reports, feature requests etc. are all welcome.

If you are new to GitHub please read [Contributing to a project](https://help.github.com/articles/fork-a-repo) for how
to send patches and pull requests to kafka-storm-starter.


<a name="License"></a>

# License

Copyright © 2014 Michael G. Noll

See [LICENSE](LICENSE) for licensing information.


<a name="References"></a>

# References


<a name="References-Wirbelsturm"></a>

## Wirbelsturm

Want to perform 1-click deployments of Kafka clusters and/or Storm clusters (with a Graphite instance, with Redis,
with...)?  Take a look at [Wirbelsturm](https://github.com/miguno/wirbelsturm), with which you can deploy such
environments locally and to Amazon AWS.


<a name="References-Kafka"></a>

## Kafka

Kafka in general:

* [Apache Kafka 0.8 basic training - Verisign](http://www.slideshare.net/miguno/apache-kafka-08-basic-training-verisign)
  -- training material (120 slides) that covers Kafka's core concepts, operating Kafka in production, and developing
  Kafka applications

Unit testing:

* [buildlackey/cep/kafka-0.8.x](https://github.com/buildlackey/cep/tree/master/kafka-0.8.x)
  -- A simple Kafka producer/consumer example with in-memory Kafka and Zookeeper instances.  For a number of reasons
  we opted not to use that code.  We list it in this section in case someone else may find it helpful.


<a name="References-Storm"></a>

## Storm

Storm in general:

* [Apache Storm 0.9 basic training - Verisign](http://www.slideshare.net/miguno/apache-storm-09-basic-training-verisign)
  -- training material (130 slides) that covers Storm's core concepts, operating Storm in production, and developing
  Storm applications
* [Storm FAQ](http://storm.apache.org/documentation/FAQ.html)
* [Config (Java API)](http://storm.apache.org/apidocs/backtype/storm/Config.html)
* [Understanding the Internal Message Buffers of Storm](http://www.michael-noll.com/blog/2013/06/21/understanding-storm-internal-message-buffers/)
* [Sending Metrics From Storm to Graphite](http://www.michael-noll.com/blog/2013/11/06/sending-metrics-from-storm-to-graphite/)

Unit testing:

* [TestingApiDemo.java](https://github.com/xumingming/storm-lib/blob/master/src/jvm/storm/TestingApiDemo.java)
  -- Demonstrates in Java how to use Storm's built-in testing API.  Unfortunately the code is more than a year old and
  not well documented.
    * Note that `backtype.storm.Testing` is apparently not well suited to test Trident topologies.
      See [Any Java example to write test cases for storm Transactional topology](https://groups.google.com/forum/#!msg/storm-user/nZs2NwNqqn8/CjKaZK7eRFsJ)
      (Mar 2013) for details.
* [MockOutputCollector](https://gist.github.com/k2xl/1782187)
  -- Code example on how to implement a mock `OutputCollector` for unit testing.
* [Testing the logic of Storm topologies](https://groups.google.com/forum/#!topic/storm-user/Magc5-vt2Hg)
  -- Discussion in the old storm-user mailing list, Dec 2011
* [buildlackey/cep/storm-kafka](https://github.com/buildlackey/cep/tree/master/storm%2Bkafka)
  -- Kafka spout integration test with an in-memory Storm cluster (`LocalCluster`), and in-memory Kafka and Zookeeper
  instances.  For a number of reasons we opted not to use that code.  We list it in this section in case someone else
  may find it helpful.
* [buildlackey/cep/esper+storm+kafka](https://github.com/buildlackey/cep/tree/master/esper%2Bstorm%2Bkafka)
  -- Example illustrating a Kafka consumer spout, a Kafka producer bolt, and an Esper streaming query bolt
* [schleyfox/storm-test](https://github.com/schleyfox/storm-test)
  -- Test utilities for Storm (in Clojure).

Kafka spout [wurstmeister/storm-kafka-0.8-plus](https://github.com/wurstmeister/storm-kafka-0.8-plus):

* [Example code on how to use the spout](https://github.com/wurstmeister/storm-kafka-0.8-plus-test)

Kafka spout [HolmesNL/kafka-spout](https://github.com/HolmesNL/kafka-spout), written by the
[Netherlands Forensics Institute](http://forensicinstitute.nl):

* [Main documentation](https://github.com/HolmesNL/kafka-spout/wiki)
* [KafkaSpout.java](https://github.com/HolmesNL/kafka-spout/blob/develop/src/main/java/nl/minvenj/nfi/storm/kafka/KafkaSpout.java)
  -- Helpful to understand how the spout works.
* [ConfigUtils.java](https://github.com/HolmesNL/kafka-spout/blob/develop/src/main/java/nl/minvenj/nfi/storm/kafka/util/ConfigUtils.java)
  -- Helpful to understand how the Kafka spout can be configured.


<a name="References-Avro"></a>

## Avro

Twitter Bijection:

* [SpecificAvroCodecsSpecification.scala](https://github.com/twitter/bijection/blob/develop/bijection-avro/src/test/scala/com/twitter/bijection/avro/SpecificAvroCodecsSpecification.scala)
  -- How to use Bijection for Avro's `Specific*` API
* [GenericAvroCodecsSpecification.scala](https://github.com/twitter/bijection/blob/develop/bijection-avro/src/test/scala/com/twitter/bijection/avro/GenericAvroCodecsSpecification.scala)
  -- How to use Bijection for Avro's `Generic*` API

Kafka:

* [How to use Kafka and Avro](http://stackoverflow.com/questions/8298308/how-to-use-kafka-and-avro)


<a name="References-Kryo"></a>

## Kryo

* [AdamKryoRegistrator.java](https://github.com/bigdatagenomics/adam/blob/master/adam-core/src/main/scala/edu/berkeley/cs/amplab/adam/serialization/AdamKryoRegistrator.scala)
  -- example on how to register serializers with Kryo
* Twitter Chill examples on how to create Avro-based serializers for Kryo:
    * [AvroSerializerSpec.scala](https://github.com/twitter/chill/blob/develop/chill-avro/src/test/scala/com/twitter/chill/avro/AvroSerializerSpec.scala)
    * [BijectionEnrichedKryo.scala](https://github.com/twitter/chill/blob/develop/chill-bijection/src/main/scala/com/twitter/chill/BijectionEnrichedKryo.scala)
