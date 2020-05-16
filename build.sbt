name := "balcony"

version := "0.1"

scalaVersion in ThisBuild := "2.13.2"


lazy val balcony = (project in file("."))
  .settings(commonSettings)
  .aggregate(database, protocol, model)

lazy val `balcony-cli` = (project in file("balcony-cli"))
  .settings(commonSettings)
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