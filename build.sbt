addCommandAlias("dist", ";riffRaffArtifact")

name := "commercial-expiry-service"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, RiffRaffArtifact)
  .settings(Defaults.coreDefaultSettings: _*)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "com.amazonaws" % "aws-java-sdk" % "1.10.8",
  "com.gu" %% "content-api-client" % "6.7"
)

packageName in Universal := normalizedName.value

riffRaffPackageType := (packageZipTarball in config("universal")).value

doc in Compile <<= target.map(_ / "none")

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
