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
  "clojars-repository" at "https://clojars.org/repo"
)

libraryDependencies ++= Seq(
  "com.twitter" %% "bijection-core" % "0.6.2",
  "com.twitter" %% "bijection-avro" % "0.6.2",
  "com.twitter" %% "chill" % "0.3.6",
  "com.twitter" % "chill-avro" % "0.3.6",
  "com.twitter" %% "chill-bijection" % "0.3.6",
  // The excludes of jms, jmxtools and jmxri are required as per https://issues.apache.org/jira/browse/KAFKA-974.
  // The exclude of slf4j-simple is because it overlaps with our use of logback with slf4j facade;  without the exclude
  // we get slf4j warnings and logback's configuration is not picked up.
  "org.apache.kafka" % "kafka_2.10" % "0.8.1.1"
    exclude("javax.jms", "jms")
    exclude("com.sun.jdmk", "jmxtools")
    exclude("com.sun.jmx", "jmxri")
    exclude("org.slf4j", "slf4j-simple")
    exclude("log4j", "log4j")
    exclude("org.apache.zookeeper", "zookeeper"),
  "org.apache.storm" % "storm-core" % "0.9.2-incubating" % "provided"
    exclude("org.apache.zookeeper", "zookeeper")
    exclude("org.slf4j", "log4j-over-slf4j"),
  "org.apache.storm" % "storm-kafka" % "0.9.2-incubating"
    exclude("org.apache.zookeeper", "zookeeper"),
  "com.101tec" % "zkclient" % "0.4"
    exclude("org.apache.zookeeper", "zookeeper"),
  "org.apache.curator" % "curator-test" % "2.4.0",
  "commons-io" % "commons-io" % "2.4",
  // Logback with slf4j facade
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "ch.qos.logback" % "logback-core" % "1.1.2",
  "org.slf4j" % "slf4j-api" % "1.7.7",
  // Test dependencies
  "org.scalatest" %% "scalatest" % "2.2.0" % "test",
  "org.mockito" % "mockito-all" % "1.9.5" % "test"
)

// Required IntelliJ workaround.  This tells `sbt gen-idea` to include scala-reflect as a compile dependency (and not
// merely as a test dependency), which we need for TypeTag usage.
libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _)

javacOptions in Compile ++= Seq(
  "-source", "1.6",
  "-target", "1.6",
  "-Xlint:unchecked",
  "-Xlint:deprecation")

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Ywarn-value-discard")

publishArtifact in Test := false

parallelExecution in ThisBuild := false

// Write test results to file in JUnit XML format
testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports/junitxml")

// Write test results to console.
//
// Tip: If you need to troubleshoot test runs, it helps to use the following reporting setup for ScalaTest.
//
//    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oUDT", "-eUDT")
//
testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-o")

// See https://github.com/scoverage/scalac-scoverage-plugin
ScoverageSbtPlugin.instrumentSettings

mainClass in (Compile,run) := Some("com.miguno.kafkastorm.storm.KafkaStormDemo")
