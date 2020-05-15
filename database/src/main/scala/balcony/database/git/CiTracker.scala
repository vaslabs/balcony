package balcony.database.git

import balcony.model.{BuildScript, CodeCommit, Commit, EnvironmentBuild, Hash, NoOutput}
import balcony.protocol.BuildProcess
import cats.effect.IO
import org.eclipse.jgit.api.Git

import scala.jdk.CollectionConverters._

class CiTracker private(git: Git) {

  def apply(command: CiTracker.Command): IO[EnvironmentBuild] = for {
    codeCommit <- codeCommitIO
    envBuild <- BuildProcess.build(codeCommit, command.buildScript, _ => IO.pure(Hash("")), IO.pure(NoOutput))
  } yield envBuild

  private def codeCommitIO: IO[CodeCommit] = IO(
    CodeCommit(Commit.fromString(git.log().call().asScala.head.toObjectId.name()))
  )
}

object CiTracker {
  def create(git: Git): CiTracker = new CiTracker(git)

  sealed trait Command {
    def buildScript: BuildScript
  }

  object Command {
    case class BuildLatestCommit(buildScript: BuildScript) extends Command
  }
}
