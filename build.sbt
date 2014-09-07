scalaVersion := "2.10.4"

scalacOptions ++= Seq("-feature", "-optimize", "-deprecation")

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io",
  "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
  "io.netty" % "netty-all" % "4.0.23.Final",
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.typesafe.play" %% "anorm" % "2.3.3",
  "org.postgresql" % "postgresql" % "9.2-1003-jdbc4",
  "io.spray" % "spray-can" % "1.3.1"
)
