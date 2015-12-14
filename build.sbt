organization := "com.miguno.kafkastorm"

name := "kafka-storm-starter"

scalaVersion := "2.10.5"

seq(sbtavro.SbtAvro.avroSettings : _*)

// Configure the desired Avro version.  sbt-avro automatically injects a libraryDependency.
(version in avroConfig) := "1.7.7"

// Look for *.avsc etc. files in src/test/avro
(sourceDirectory in avroConfig) <<= (sourceDirectory in Compile)(_ / "avro")

(stringType in avroConfig) := "String"

// https://github.com/jrudolph/sbt-dependency-graph
net.virtualvoid.sbt.graph.Plugin.graphSettings

resolvers ++= Seq(
  "typesafe-repository" at "http://repo.typesafe.com/typesafe/releases/",
  "clojars-repository" at "https://clojars.org/repo"
)

val bijectionVersion = "0.7.1"
val chillVersion = "0.5.1"
val sparkVersion = "1.1.1"
val stormVersion = "0.9.6"

libraryDependencies ++= Seq(
  "com.twitter" %% "bijection-core" % bijectionVersion,
  "com.twitter" %% "bijection-avro" % bijectionVersion,
  "com.twitter" %% "chill" % chillVersion,
  "com.twitter" %% "chill-avro" % chillVersion,
  "com.twitter" %% "chill-bijection" % chillVersion,
  // The excludes of jms, jmxtools and jmxri are required as per https://issues.apache.org/jira/browse/KAFKA-974.
  // The exclude of slf4j-simple is because it overlaps with our use of logback with slf4j facade;  without the exclude
  // we get slf4j warnings and logback's configuration is not picked up.
  "org.apache.kafka" % "kafka_2.10" % "0.8.2.2"
    exclude("javax.jms", "jms")
    exclude("com.sun.jdmk", "jmxtools")
    exclude("com.sun.jmx", "jmxri")
    exclude("org.slf4j", "slf4j-simple")
    exclude("log4j", "log4j")
    exclude("org.apache.zookeeper", "zookeeper")
    exclude("com.101tec", "zkclient"),
  "org.apache.storm" % "storm-core" % stormVersion % "provided"
    exclude("org.apache.zookeeper", "zookeeper")
    exclude("org.slf4j", "log4j-over-slf4j"),
  "org.apache.storm" % "storm-kafka" % stormVersion
    exclude("org.apache.zookeeper", "zookeeper"),
  "org.apache.spark" %% "spark-core" % sparkVersion
    exclude("org.apache.zookeeper", "zookeeper")
    exclude("org.slf4j", "slf4j-api")
    exclude("org.slf4j", "slf4j-log4j12")
    exclude("org.slf4j", "jul-to-slf4j")
    exclude("org.slf4j", "jcl-over-slf4j")
    exclude("com.twitter", "chill_2.10")
    exclude("log4j", "log4j"),
  "org.apache.spark" %% "spark-streaming-kafka" % sparkVersion
    exclude("org.apache.zookeeper", "zookeeper"),
  "com.101tec" % "zkclient" % "0.4"
    exclude("org.apache.zookeeper", "zookeeper"),
  "org.apache.curator" % "curator-test" % "2.4.0"
    exclude("org.jboss.netty", "netty")
    exclude("org.slf4j", "slf4j-log4j12"),
  "commons-io" % "commons-io" % "2.4",
  "org.apache.commons" % "commons-pool2" % "2.3",
  // Logback with slf4j facade
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  // Test dependencies
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "org.mockito" % "mockito-all" % "1.9.5" % "test"
)

// Required IntelliJ workaround.  This tells `sbt gen-idea` to include scala-reflect as a compile dependency (and not
// merely as a test dependency), which we need for TypeTag usage.
libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _)

// Enable forking (see sbt docs) because our full build (including tests) uses many threads.
fork := true

// The following options are passed to forked JVMs.
//
// Note: If you need to pass options to the JVM used by sbt (i.e. the "parent" JVM), then you should modify `.sbtopts`.
javaOptions ++= Seq(
  "-Xms256m",
  "-Xmx512m",
  "-XX:+UseG1GC",
  "-XX:MaxGCPauseMillis=20",
  "-XX:InitiatingHeapOccupancyPercent=35",
  "-Djava.awt.headless=true",
  "-Djava.net.preferIPv4Stack=true")

javacOptions in Compile ++= Seq(
  "-source", "1.7",
  "-target", "1.7",
  "-Xlint:unchecked",
  "-Xlint:deprecation")

scalacOptions ++= Seq(
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

scalacOptions in Compile ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-feature",  // Emit warning and location for usages of features that should be imported explicitly.
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xlint", // Enable recommended additional warnings.
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
  "-Ywarn-dead-code",
  "-Ywarn-value-discard" // Warn when non-Unit expression results are unused.
)

scalacOptions in Test ~= { (options: Seq[String]) =>
  options.filterNot(_ == "-Ywarn-value-discard").filterNot(_ == "-Ywarn-dead-code" /* to fix warnings due to Mockito */)
}

scalacOptions in ScoverageTest ~= { (options: Seq[String]) =>
  options.filterNot(_ == "-Ywarn-value-discard").filterNot(_ == "-Ywarn-dead-code" /* to fix warnings due to Mockito */)
}

publishArtifact in Test := false

parallelExecution in ThisBuild := false

// Write test results to file in JUnit XML format
testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports/junitxml")

// Write test results to console.
//
// Tip: If you need to troubleshoot test runs, it helps to use the following reporting setup for ScalaTest.
//      Notably these suggested settings will ensure that all test output is written sequentially so that it is easier
//      to understand sequences of events, particularly cause and effect.
//      (cf. http://www.scalatest.org/user_guide/using_the_runner, section "Configuring reporters")
//
//        testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oUDT", "-eUDT")
//
//        // This variant also disables ANSI color output in the terminal, which is helpful if you want to capture the
//        // test output to file and then run grep/awk/sed/etc. on it.
//        testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oWUDT", "-eWUDT")
//
testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-o")

// See https://github.com/scoverage/scalac-scoverage-plugin
instrumentSettings

mainClass in (Compile,run) := Some("com.miguno.kafkastorm.storm.topologies.KafkaStormDemo")
