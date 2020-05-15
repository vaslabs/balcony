package balcony.database.git

import java.io.{File, FileWriter}
import java.nio.file.{Files, Path}

import balcony.model.{BuildScript, Commit, Environment, Hash}
import cats.effect.IO
import org.eclipse.jgit.api.Git
import org.scalacheck.Gen
import cats.effect._
import cats.implicits._

case class RuntimeDependencies private(git: Git, repoLocation: Path, commits: List[Commit], buildScript: BuildScript)



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
  } yield RuntimeDependencies(git, path, commits, buildScript)

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
}