package balcony.database.git

import java.io.{File, FileWriter}
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path, StandardOpenOption}

import balcony.model.{BuildScript, Commit, Environment, Hash}
import cats.effect.IO
import org.eclipse.jgit.api.Git
import org.scalacheck.Gen
import cats.effect._
import cats.implicits._

import scala.jdk.CollectionConverters._

case class RuntimeDependencies private(
        git: Git,
        repoLocation: Path,
        commits: List[Commit],
        buildScript: BuildScript,
        buildLog: List[String]
)

object RuntimeDependencies {
  def gen(): Gen[RuntimeDependencies] = for {
    path <- Gen.const(Files.createTempDirectory("balcony-"))
    files <- Gen.nonEmptyListOf(
      Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString).map(
        name => new File(s"${path.toFile.getAbsolutePath}/${name}")
      )
    )
    content <- Gen.listOfN(
      files.size,
      Gen.alphaNumStr
    )
    fileSystem = files.lazyZip(content).toMap
    git = IO(Git.init().setDirectory(path.toFile).call()).unsafeRunSync()
    commits = initialiseRepo(git, fileSystem).unsafeRunSync()
    buildScript <- buildScriptGen(git)
  } yield RuntimeDependencies(git, path, commits, buildScript, List.empty)

  private def initialiseRepo(git: Git, fileSystem: Map[File, String]): IO[List[Commit]] = IO.suspend(
    fileSystem.map {
      case (file, content) =>
        writeContent(file, content) *> commitFile(git, file)
    }.toList.sequence
  )

  private def writeContent(file: File, str: String): IO[Unit] = Resource.fromAutoCloseable(
    IO.delay(new FileWriter(file)).handleErrorWith(t => IO.delay(t.printStackTrace()) *> IO.raiseError(t))
  ).use(writer => IO(writer.write(str)))

  private def commitFile(git: Git, file: File): IO[Commit] = IO {
    git.add().addFilepattern(".").call()
    Commit.fromString(
      git.commit().setAuthor("Balcony", "balcony@vaslabs.io")
        .setMessage(s"Added file: ${file.getName}")
        .call().toObjectId.name()
    )
  }

  private def buildScriptGen(git: Git): Gen[BuildScript] = for {
    name <- Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
    reference <- Gen.alphaNumStr.map(Hash.apply)
    environment <- Gen.alphaNumStr.map(Environment.apply)
    _ = generateBuildFile(git, name, List("hello")).unsafeRunSync()
  } yield BuildScript(s".builds/$name", name, reference, environment)

  implicit final class RuntimeDependenciesOps(val gen: Gen[RuntimeDependencies]) extends AnyVal {
    def withBuildScript: Gen[RuntimeDependencies] = for {
      runtimeDeps <- gen
      messages <- Gen.nonEmptyListOf(Gen.asciiPrintableStr.map(_.filterNot(_ == '\'').filterNot(_ == '\\')))
      buildScript <- genBuildScript(runtimeDeps, messages)
    } yield runtimeDeps.copy(buildScript = buildScript, buildLog = messages)
  }

  private def genBuildScript(runtimeDependencies: RuntimeDependencies, messages: List[String]): Gen[BuildScript] = {
    val buildHash = generateBuildFile(
      runtimeDependencies.git,
      runtimeDependencies.buildScript.name,
      messages
    ).unsafeRunSync()
    BuildScript(
      s".builds/${runtimeDependencies.buildScript.name}",
      runtimeDependencies.buildScript.name,
      buildHash,
      runtimeDependencies.buildScript.environment
    )
  }

  private def generateBuildFile(git: Git, buildName: String, messages: List[String]): IO[Hash] = {
    val script = "#!/bin/bash" +: messages.map(msg => s"echo '${msg}'")
    val ciConfiguration = CiConfiguration.setUp(git)
    ciConfiguration.flatMap(_.writeBuild(buildName, script))
  }
}

case class BuildScriptSetup(name: String, lines: List[String])

object BuildScriptSetup {
  def gen: Gen[BuildScriptSetup] = for {
    name <- Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
    commands <- Gen.listOf(Gen.asciiPrintableStr.map(_.filterNot(_ == '\'').filterNot(_ == '\\')))
  } yield BuildScriptSetup(name, commands)
}