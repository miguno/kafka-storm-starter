organization := "com.miguno.kafkastorm"

name := "kafka-storm-starter"

scalaVersion := "2.10.4"

seq(sbtavro.SbtAvro.avroSettings : _*)

// Configure the desired Avro version.  sbt-avro automatically injects a libraryDependency.
(version in avroConfig) := "1.7.6"

// Look for *.avsc etc. files in src/test/avro
(sourceDirectory in avroConfig) <<= (sourceDirectory in Compile)(_ / "avro")

(stringType in avroConfig) := "String"

// https://github.com/jrudolph/sbt-dependency-graph
net.virtualvoid.sbt.graph.Plugin.graphSettings

resolvers ++= Seq(
  "typesafe-repository" at "http://repo.typesafe.com/typesafe/releases/",
  "clojars-repository" at "https://clojars.org/repo",
  // For retrieving Kafka release artifacts directly from Apache.  The artifacts are also available via Maven Central.
  "Apache releases" at "https://repository.apache.org/content/repositories/releases/"
)

libraryDependencies ++= Seq(
  "com.twitter" %% "bijection-core" % "0.6.2",
  "com.twitter" %% "bijection-avro" % "0.6.2",
  // Chill uses Kryo 2.21, which is not fully compatible with 2.17 (used by Storm).
  // We must exclude the newer Kryo version, otherwise we run into the problem described at
  // https://github.com/thinkaurelius/titan/issues/301.
  //
  // TODO: Once Storm 0.9.2 is released we can update our dependencies to use Chill as-is (without excludes) because
  //       Storm then uses Kryo 2.21 (via Carbonite 1.3.3) just like Chill does.
  "com.twitter" %% "chill" % "0.3.6"
    exclude("com.esotericsoftware.kryo", "kryo"),
  "com.twitter" % "chill-avro" % "0.3.6"
    exclude("com.esotericsoftware.kryo", "kryo"),
  "com.twitter" %% "chill-bijection" % "0.3.6"
    exclude("com.esotericsoftware.kryo", "kryo"),
  // The excludes of jms, jmxtools and jmxri are required as per https://issues.apache.org/jira/browse/KAFKA-974.
  // The exclude of slf4j-simple is because it overlaps with our use of logback with slf4j facade;  without the exclude
  // we get slf4j warnings and logback's configuration is not picked up.
  "org.apache.kafka" % "kafka_2.10" % "0.8.1.1"
    exclude("javax.jms", "jms")
    exclude("com.sun.jdmk", "jmxtools")
    exclude("com.sun.jmx", "jmxri")
    exclude("org.slf4j", "slf4j-simple"),
  "org.apache.storm" % "storm-core" % "0.9.1-incubating" % "provided"
    exclude("org.slf4j", "log4j-over-slf4j"),
  // We exclude curator-framework because storm-kafka-0.8-plus recently switched from curator 1.0.1 to 1.3.3, which
  // pulls in a newer version of ZooKeeper with which Storm 0.9.1 is not yet compatible.
  //
  // TODO: Remove the exclude once Storm 0.9.2 is released, because that version depends on a newer version (3.4.x) of
  //       ZooKeeper.
  "com.miguno" %% "storm-kafka-0.8-plus" % "0.5.0-SNAPSHOT"
    exclude("com.netflix.curator", "curator-framework"),
  "com.netflix.curator" % "curator-test" % "1.0.1",
  "com.101tec" % "zkclient" % "0.4",
  // Logback with slf4j facade
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "ch.qos.logback" % "logback-core" % "1.1.2",
  "org.slf4j" % "slf4j-api" % "1.7.7",
  // Test dependencies
  "org.scalatest" %% "scalatest" % "2.1.5" % "test",
  "org.mockito" % "mockito-all" % "1.9.5" % "test"
)

// Required IntelliJ workaround.  This tells `sbt gen-idea` to include scala-reflect as a compile dependency (and not
// merely as a test dependency), which we need for TypeTag usage.
libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _)

publishArtifact in Test := false

parallelExecution in Test := false

// Write test results to file in JUnit XML format
testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports/junitxml")

// Write test results to console/stdout
testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-o")

// See https://github.com/scoverage/scalac-scoverage-plugin
ScoverageSbtPlugin.instrumentSettings

mainClass in (Compile,run) := Some("com.miguno.kafkastorm.storm.KafkaStormDemo")
