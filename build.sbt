addCommandAlias("dist", ";riffRaffArtifact")

name := "commercial-expiry-service"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, RiffRaffArtifact)
  .settings(Defaults.coreDefaultSettings: _*)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  specs2,
  "com.amazonaws" % "aws-java-sdk" % "1.10.26",
  "com.gu" %% "content-api-client" % "6.7",
  "com.google.api-ads" % "dfp-axis" % "2.5.0",
  "net.logstash.logback" % "logstash-logback-encoder" % "4.5.1"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

packageName in Universal := normalizedName.value

riffRaffPackageType := (packageZipTarball in config("universal")).value
riffRaffPackageName := s"editorial-tools:${name.value}"
riffRaffManifestProjectName := riffRaffPackageName.value
riffRaffBuildIdentifier :=  Option(System.getenv("CIRCLE_BUILD_NUM")).getOrElse("dev")
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffManifestBranch := Option(System.getenv("CIRCLE_BRANCH")).getOrElse("dev")

doc in Compile <<= target.map(_ / "none")

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
