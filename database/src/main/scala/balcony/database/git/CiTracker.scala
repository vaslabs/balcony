package balcony.database.git

import java.io.File
import java.nio.file.{Files, Path, StandardOpenOption}
import java.nio.ByteBuffer

import balcony.model.{BuildScript, CodeCommit, Commit, EnvironmentBuild, Hash, NoOutput}
import balcony.protocol.BuildProcess
import cats.effect.{IO, Resource}
import org.eclipse.jgit.api.Git
import balcony.database.json.circe._
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode

import scala.jdk.CollectionConverters._

class CiTracker private(git: Git) {

  val repoLocation = git.getRepository.getDirectory.getParentFile

  def apply(command: CiTracker.Command): IO[EnvironmentBuild] = for {
    codeCommit <- codeCommitIO
    _ <- checkBranch
    envBuild <- openCiTracker(false, false).use(_ =>
        BuildProcess.build(repoLocation.getAbsolutePath, codeCommit, command.buildScript, commitLog, IO.pure(NoOutput))
    )
    _ <- persist(envBuild)
  } yield envBuild

  private def commitLog(path: Path): IO[Hash] = IO {
    git.add().addFilepattern(path.toFile.getName).call()
  } *> IO (
    Hash(
      git.commit()
        .setMessage(s"Added logs for build ${path.toFile.getName}").call()
        .toObjectId.name()
    )
  )

  def apply(query: CiTracker.Query): IO[EnvironmentBuild] =
    openCiTracker(false, false).use(_ => readLatest)

  private def checkBranch: IO[Unit] = IO(
    git.branchList().call().asScala.find(_.getName == CiTracker.Branch)
  ).flatMap(
    _.fold(openCiTracker(true, true).use(_ => IO.unit))(
      _ => IO.unit
    )
  )

  private def codeCommitIO: IO[CodeCommit] = IO(
    CodeCommit(Commit.fromString(git.log().call().asScala.head.toObjectId.name()))
  )


  private def openCiTracker(createBranch: Boolean, orphan: Boolean) = {
    Resource.make(
      IO(git.getRepository.getFullBranch).flatMap( branchName =>
        IO(
          git.checkout()
           .setOrphan(orphan)
           .setCreateBranch(createBranch)
           .setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM)
           .setName(CiTracker.Branch).call()
        ) *> IO.pure(branchName)
    ))(prevBranch =>
      IO(git.checkout().setName(prevBranch).call()) *>  IO.unit
    )
  }

  private def readLatest(): IO[EnvironmentBuild] =
    IO {
      val file = new File(s"${repoLocation.getAbsolutePath}/${CiTracker.FileNames.Latest}")
      ByteBuffer.wrap(Files.readAllBytes(file.toPath))
    }.flatMap(Codecs.decode[EnvironmentBuild](_)
  )

  private def persist(build: EnvironmentBuild): IO[Unit] =
    openCiTracker(false, false).use(_ =>
      writeEnvironmentBuild(build) *> IO(
        git.commit().setMessage(s"Finished build ${build.toString}").call()
      ) *> IO.unit
    )

  private def writeEnvironmentBuild(build: EnvironmentBuild): IO[Unit] =
    IO {
      Files.write(
        new File(s"${repoLocation.getAbsolutePath}/${CiTracker.FileNames.Latest}").toPath,
        Codecs.encode(build).array(),
        StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW
      )
    } *> IO.unit
}

object CiTracker {
  def create(git: Git): CiTracker = new CiTracker(git)

  sealed trait Command {
    def buildScript: BuildScript
  }

  sealed trait Query

  object Command {
    case class BuildLatestCommit(buildScript: BuildScript) extends Command

    case class LatestBuild() extends Query

  }

  object FileNames {
    private[CiTracker] final val Latest = "latest"
  }
  final val Branch = "balcony/ci-tracker"
}
