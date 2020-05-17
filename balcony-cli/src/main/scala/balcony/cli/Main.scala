package balcony.cli

import java.io.File

import balcony.database.git.CiTracker.Command.BuildLatestCommit
import balcony.database.git.{CiConfiguration, CiTracker}
import balcony.model.{Environment, EnvironmentBuild, Hash}
import cats.Show
import cats.effect.{ExitCode, IO, Resource}
import com.monovore.decline._
import com.monovore.decline.effect._
import cats.implicits._
import org.eclipse.jgit.api.Git

import scala.io.{BufferedSource, Codec, Source}

object Main extends CommandIOApp(
  name = "balcony",
  header = "Portable CI/CD for your source code under git",
  version = "0.0.1-beta"
) {
  import BalconyOpts._
  import Report._
  override def main: Opts[IO[ExitCode]] = (setupCommand orElse defineBuildCommand orElse runBuildCommand).map {
    case Setup(location) =>
      CiConfiguration.setUp(new File(location).toPath).handleErrorWith(_ => IO(ExitCode.Error)) *>
        IO(ExitCode.Success)
    case DefineBuildJob(name, file) =>
      val defineBuild = for {
        ciConfiguration <- CiConfiguration.create(new File("."))
        writeBuild = script => ciConfiguration.writeBuild(name, script)
        committedBuild <- file.fold(readFromStdin(writeBuild))(readFromFile(_, writeBuild))
        _ = report(committedBuild)
      } yield ExitCode.Success

      defineBuild.handleErrorWith(_ => IO(ExitCode.Error)) *> IO(ExitCode.Success)

    case RunBuild(name, environment) =>
      CiTracker.create(Git.open(new File(".")))
        .apply(BuildLatestCommit(name, environment))
        .flatMap(report(_))
        .handleErrorWith(t => IO(t.printStackTrace()) *> IO(ExitCode.Error)) *> IO(ExitCode.Success)

  }

  private def readFromStdin[A](andThen: LazyList[String] => IO[A]): IO[A] =
    readFromResource(Resource.fromAutoCloseable(IO(Source.fromInputStream(System.in)(Codec.UTF8))), andThen)

  private def readFromFile[A](file: String, andThen: LazyList[String] => IO[A]): IO[A] =
    readFromResource(
      Resource.fromAutoCloseable(IO(Source.fromFile(new File(file)))),
      andThen
    )

  private def readFromResource[A](resource: Resource[IO, BufferedSource], andThen: LazyList[String] => IO[A]): IO[A] =
    resource.use(
      source => IO(LazyList.from(source.getLines())).flatMap(andThen)
    )

  private def report[A : Show](a: A) =
    IO(println(a.show))
}

object Report {
  implicit val showHash: Show[Hash] = (h: Hash) => h.show
  implicit val showEnvironmentBuild: Show[EnvironmentBuild] = Show.fromToString
}

object  BalconyOpts {
  case class Setup(location: String)
  case class DefineBuildJob(name: String, file: Option[String])
  case class RunBuild(name: String, environment: Environment)

  val setupCommand = Opts.subcommand("init", "Initialise balcony for this git repository") {
    Opts.argument[String]("location").withDefault(".")
  }.map(Setup)

  val defineBuildCommand = Opts.subcommand("build-job", "Define a job and a script to execute") {
    (
      Opts.option[String](long = "name", "The name of the job", "n", metavar = "name"),
      Opts.option[String](long = "file", help = "The file that contains the script - if not supplied stdin will be used")
        .orNone
    ).mapN(DefineBuildJob)
  }

  val runBuildCommand = Opts.subcommand("exec", "Execute a build job script") {
    (
      Opts.option[String](long = "name", "The name of the build job pre-defined in build-job"),
      Opts.option[String](long = "env", help = "A user defined environment for the command", metavar = "environment")
        .map(Environment)
    ).mapN(RunBuild)
  }

}