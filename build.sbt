name := "scala-playground"

version := "0.1"

scalaVersion := "2.13.7"

idePackagePrefix := Some("com.agilogy.playground")

libraryDependencies += "org.typelevel" %% "cats-effect" % "3.2.9"
libraryDependencies += "co.fs2" %% "fs2-core" % "3.2.0"
libraryDependencies += "co.fs2" %% "fs2-io" % "3.2.0"
libraryDependencies +=   "org.typelevel" %% "log4cats-slf4j"   % "2.1.1"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.32"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.6"
libraryDependencies +=  "com.disneystreaming" %% "weaver-cats" % "0.7.7" % Test
testFrameworks += new TestFramework("weaver.framework.CatsEffect")