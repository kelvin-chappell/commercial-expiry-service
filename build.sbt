name := "commercial-expiry-service"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  specs2 % Test,
  "com.amazonaws" % "aws-java-sdk" % "1.10.0",
  "com.gu" %% "configuration" % "4.0",
  "com.gu" %% "content-api-client" % "6.6"
)

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
