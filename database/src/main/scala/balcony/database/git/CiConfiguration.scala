package balcony.database.git

import java.io.File
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path, StandardOpenOption}

import balcony.model.{BuildConfiguration, Commit, Hash}
import cats.effect.{IO, Resource}
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand.ListMode

import scala.jdk.CollectionConverters._
import scala.jdk.StreamConverters._

class CiConfiguration private (git: Git) {
  def listBuilds(): IO[List[BuildConfiguration]] =
    CiConfiguration.openCiTracker(false, false)(git)
      .use(_ =>
        IO {
          val builds = Files.list(CiConfiguration.absoluteFilePath(".builds")(git))
          builds.toScala(LazyList).map(_.toFile.getName).filterNot(_ == ".gitkeep").map {
            buildName =>
              val scriptLocation = s".builds/$buildName"
              val revision = git.log().addPath(scriptLocation).call().asScala.head
              BuildConfiguration(buildName, Commit.fromString(revision.toObjectId.name()))
          }.toList
        }
      )

  def writeBuild(name: String, lines: List[String]): IO[Hash] =
      CiConfiguration.openCiTracker(false, false)(git).use(_ => IO {
        val targetLocation = s".builds/$name"
        val path = CiConfiguration.absoluteFilePath(targetLocation)(git)
        Files.write(
          path,
          lines.asJava,
          StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
        )
        Files.setPosixFilePermissions(path, Set(
          PosixFilePermission.OWNER_EXECUTE,
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.OTHERS_READ).asJava)
        git.add().addFilepattern(targetLocation).call()
        Hash(
          git
            .commit()
            .setMessage("Added build file")
            .call()
            .toObjectId
            .name()
        )
      }
  )

}

object CiConfiguration {
  def setUp(path: Path): IO[CiConfiguration] = IO {
    Git.init().setDirectory(path.toFile).call()
  }.flatMap(setUp)

  def setUp(git: Git): IO[CiConfiguration] = checkBranch(git)
    .flatMap(_ => createDirs(git).map(_ => new CiConfiguration(git)))

  private def createDirs(git: Git): IO[Unit] = openCiTracker(false, false)(git).use( _ =>
    IO{
      val gitkeep = absoluteFilePath(".builds/.gitkeep")(git)
      if (!gitkeep.toFile.exists()) {
        Files.createDirectories(absoluteFilePath(".builds")(git))
        Files.createFile(gitkeep)
        git.add().addFilepattern(".builds").call()
        git.commit().setMessage("Ci configuration: initial commit").call()
      }
    } *> IO.unit
  )

  private[git] def checkBranch(git: Git): IO[Unit] =
    IO(
      git.branchList().setListMode(ListMode.ALL).call().asScala
        .find(_.getName == s"refs/heads/${CiTracker.Branch}")
    ).flatMap(
      _.fold(openCiTracker(true, true)(git).use(_ => IO.unit))(
        _ => IO.unit
      )
    )

  private[git] def openCiTracker(createBranch: Boolean, orphan: Boolean)(git: Git) = {
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

  private[git] def absoluteFilePath(location: String)(git: Git): Path =
    new File(s"${git.getRepository.getDirectory.getParentFile}/$location").toPath
}
