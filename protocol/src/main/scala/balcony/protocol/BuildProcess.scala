package balcony.protocol

import java.io.{File, FileOutputStream, OutputStream}
import java.nio.file.{Files, Path}

import balcony.model.Build.{Failure, Success}
import balcony.model._
import cats.effect.IO

import scala.sys.process.Process

object BuildProcess {

  def build(
    codeCommit: CodeCommit,
    buildScript: BuildScript,
    environment: Environment,
    commitBuild: Path => IO[Hash],
    summariseOutput: IO[BuildOutput]
  ): IO[EnvironmentBuild] = {
    val buildIO = processExecute(buildScript.location, codeCommit, commitBuild)
    for {
      build <- buildIO
      buildOutput <- summariseOutput
    } yield EnvironmentBuild(build, buildOutput, environment, buildScript.reference)
  }

  private def processExecute(path: Path, codeCommit: CodeCommit, commitBuild: Path => IO[Hash]): IO[Build] = {
    for {
      out <- IO.delay(outputStream(codeCommit))
      path = out._1
      outputStream = out._2
      process = Process.apply(path.toFile) #> outputStream #> System.out
      exitValue <- IO.delay(process.run(false).exitValue())
      buildCommit <- commitBuild(path)
    } yield Build(codeCommit, buildCommit, parseOutcome(exitValue))
  }

  private def outputStream(codeCommit: CodeCommit): (Path, OutputStream) = {
    val outputFile = Files.createFile(new File(codeCommit.show).toPath)
    val outputStream = new FileOutputStream(outputFile.toFile)
    (outputFile, outputStream)
  }

  private val parseOutcome: Int => Build.Result = {
    case 0 => Success
    case errorCode => Failure(errorCode)
  }

}