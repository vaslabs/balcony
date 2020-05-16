package balcony.database.git

import java.io.File
import java.nio.file.{Files, Path, StandardOpenOption}
import java.nio.ByteBuffer

import balcony.database.git.CiTracker.MetaData
import balcony.database.git.CiTracker.MetaQuery.LatestLogs
import balcony.model.{BuildScript, CodeCommit, Commit, EnvironmentBuild, Hash, NoOutput}
import balcony.protocol.BuildProcess
import cats.effect.{IO, Resource}
import org.eclipse.jgit.api.Git
import balcony.database.json.circe._
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode
import org.eclipse.jgit.api.ListBranchCommand.ListMode

import scala.jdk.CollectionConverters._

class CiTracker private(git: Git) {

  final val repoLocation = git.getRepository.getDirectory.getParentFile

  def apply(command: CiTracker.Command): IO[EnvironmentBuild] = for {
    codeCommit <- codeCommitIO
    _ <- checkBranch
    envBuild <- openCiTracker(false, false).use(_ =>
        BuildProcess.build(
          repoLocation.getAbsolutePath,
          codeCommit,
          command.buildScript,
          _ => commitLog,
          IO.pure(NoOutput)
        )
    )
    _ <- persist(envBuild)
  } yield envBuild

  def apply(query: CiTracker.Query): IO[EnvironmentBuild] =
    openCiTracker(false, false).use(_ => readLatest())

  def apply(metaQuery: CiTracker.MetaQuery[List[String]]): IO[MetaData[List[String]]] =
    metaQuery match {
      case LatestLogs() =>
        openCiTracker(false, false) use (_ => readLatestLogs())
    }

  private def commitLog(): IO[Hash] = IO {
    git.add().addFilepattern(".").call()
  } *> IO (
    Hash(
      git.commit()
        .setMessage(s"Build log").call()
        .toObjectId.name()
    )
  )

  private def checkBranch: IO[Unit] =
  IO(
    git.branchList().setListMode(ListMode.ALL).call().asScala
      .find(_.getName == s"refs/heads/${CiTracker.Branch}")
  ).flatMap(
    _.fold(openCiTracker(true, true).use(_ => IO.unit))(
      _ => IO.unit
    )
  )

  private def codeCommitIO: IO[CodeCommit] = IO(
    CodeCommit(Commit.fromString(git.log().call().asScala.head.toObjectId.name()))
  )


  private[git] def openCiTracker(createBranch: Boolean, orphan: Boolean) = {
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

  private def readLatestLogs(): IO[MetaData[List[String]]] =
    for {
      latestBuild <- readLatest()
      file = new File(s"${repoLocation}/${latestBuild.build.codeCommit.show}")
      content <- IO(Files.readAllLines(file.toPath).asScala.toList)
    } yield MetaData(content)

  private def persist(build: EnvironmentBuild): IO[Unit] =
    openCiTracker(false, false).use(_ =>
      writeEnvironmentBuild(build) *> IO {
        git.add().addFilepattern(".").call()
        git.commit().setMessage(s"Finished build ${build.toString}").call()
      } *> IO.unit
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
  sealed trait MetaQuery[A]

  case class MetaData[A](data: A)

  object Command {
    case class BuildLatestCommit(buildScript: BuildScript) extends Command
  }

  object Query {
    case class LatestBuild() extends Query
  }

  object MetaQuery {
    case class LatestLogs() extends MetaQuery[List[String]]
  }

  object FileNames {
    private[CiTracker] final val Latest = "latest"
  }
  final val Branch = "balcony/ci-tracker"
}
