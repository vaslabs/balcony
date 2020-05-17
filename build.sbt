name := "balcony"

version := "0.1"

scalaVersion in ThisBuild := "2.13.2"


lazy val balcony = (project in file("."))
  .settings(commonSettings)
  .aggregate(database, protocol, model, `balcony-cli`)

lazy val `balcony-cli` = (project in file("balcony-cli"))
  .enablePlugins(JavaAppPackaging, RpmPlugin)
  .settings(commonSettings)
  .settings(rpmSettings)
  .settings(libraryDependencies ++= Dependencies.Module.cli)
  .dependsOn(database)

lazy val database = (project in file("database"))
  .settings(libraryDependencies ++= Dependencies.Module.database)
  .settings(commonSettings)
  .dependsOn(protocol)

lazy val protocol = (project in file("protocol"))
  .settings(libraryDependencies ++= Dependencies.Module.protocol)
  .settings(commonSettings)
  .dependsOn(model)

lazy val model = (project in file("model"))
  .settings(commonSettings)

lazy val commonSettings = Seq(
  scalaVersion in ThisBuild := "2.13.2"
)


lazy val rpmSettings = Seq(
  rpmVendor := "vaslabs.io",
  version in Rpm := version.value,
  rpmRelease := "6",
  packageSummary in Rpm := "Portable CI/CD solution",
  packageDescription in Rpm := "Control CI/CD within your project's source code under GIT",
  maintainerScripts in Rpm := Map.empty,
  rpmLicense := Some("Apache License 2.0"),
  maintainer := "vaslabs.io"
)