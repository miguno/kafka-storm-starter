import AssemblyKeys._

assemblySettings

// Any customized settings must be written here, i.e. after 'assemblySettings' above.
// See https://github.com/sbt/sbt-assembly for available parameters.

// Include "provided" dependencies back to run/test tasks' classpath.
// See:
// https://github.com/sbt/sbt-assembly#-provided-configuration
// http://stackoverflow.com/a/21803413/3827
//
// In our case, the Storm dependency must be set to "provided (cf. `build.sbt`) because, when deploying and launching
// our Storm topology code "for real" to a distributed Storm cluster, Storm wants us to exclude the Storm dependencies
// (jars) as they are provided [no pun intended] by the Storm cluster.
run in Compile <<= Defaults.runTask(fullClasspath in Compile, mainClass in (Compile, run), runner in (Compile, run))

mergeStrategy in assembly <<= (mergeStrategy in assembly) {
  (old) => {
    case s if s.endsWith(".class") => MergeStrategy.last
    case x => old(x)
  }
}

// We do not want to run the test when assembling because we prefer to chain the various build steps manually, e.g.
// via `./sbt clean test scoverage:test package packageDoc packageSrc doc assembly`.
//
// Apart from reducing total build time time (we already ran the tests prior to assembly, so there is no need to run
// them again), there is another reason:  Unfortunately, when running assembly with tests enabled, the example sbt chain
// above may fail;  this seems to be due to orphaned processes/threads (e.g. ZooKeeper) that are not fully terminate
// when sbt decides to execute the assembly step.
//
// Comment the following line if you do want to (re-)run all the tests before building assembly.
test in assembly := {}
