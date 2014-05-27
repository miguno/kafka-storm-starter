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
