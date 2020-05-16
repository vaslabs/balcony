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
    buildScript <- buildScriptGen
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

  private def buildScriptGen: Gen[BuildScript] = for {
    location <- Gen.alphaNumStr
    name <- Gen.alphaNumStr
    reference <- Gen.alphaNumStr.map(Hash.apply)
    environment <- Gen.alphaNumStr.map(Environment.apply)
  } yield BuildScript(location, name, reference, environment)

  implicit final class RuntimeDependenciesOps(val gen: Gen[RuntimeDependencies]) extends AnyVal {
    def withBuildScript: Gen[RuntimeDependencies] = for {
      runtimeDeps <- gen
      messages <- Gen.nonEmptyListOf(Gen.asciiPrintableStr.map(_.filterNot(_ == '\'')))
      buildScript <- genBuildScript(runtimeDeps, messages)
    } yield runtimeDeps.copy(buildScript = buildScript, buildLog = messages)
  }

  private def genBuildScript(runtimeDependencies: RuntimeDependencies, messages: List[String]): Gen[BuildScript] = {
    val buildHash = generateBuildFile(runtimeDependencies.git, messages).unsafeRunSync()
    BuildScript("build_echo.sh", "build", buildHash, runtimeDependencies.buildScript.environment)
  }

  private def generateBuildFile(git: Git, messages: List[String]): IO[Hash] = {
    val ciTracker = CiTracker.create(git)
    val script = "#!/bin/bash" +: messages.map(msg => s"echo '${msg}'")

    ciTracker
      .openCiTracker(true, true)
      .use(_ =>
        IO {
          val location = s"${ciTracker.repoLocation.getAbsolutePath}/build_echo.sh"
          val file = new File(location).toPath
          Files.write(
            file,
            script.asJava,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
          )
          Files.setPosixFilePermissions(file, Set(
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ).asJava)
          git.add().addFilepattern("build_echo.sh").call()
          Hash(
            git
              .commit()
              .setMessage("Added build file")
              .setAuthor("Balcony", "balcony@vaslabs.io")
              .call()
              .toObjectId
              .name()
          )
        }.handleErrorWith(t => IO(t.printStackTrace()) *> IO.raiseError(t))
      )

  }
}