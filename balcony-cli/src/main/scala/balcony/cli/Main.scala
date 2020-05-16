package balcony.cli

import java.io.File

import cats.effect.{ExitCode, IO}
import com.monovore.decline._
import com.monovore.decline.effect._

object Main extends CommandIOApp(
  name = "balcony",
  header = "Portable CI/CD for your source code under git",
  version = "0.0.1-beta"
) {
  override def main: Opts[IO[ExitCode]] = ???
}


object  BalconyOpts {
  case class Setup(location: String)
  case class Build()

  val setupCommand = Opts.subcommand("init", "Initialise balcony for this git repository") {
    Opts.argument("location").orElse(Opts("."))
  }.map(Setup)

}