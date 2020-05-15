import sbt._

object Dependencies {

  object Version {
    val jgit = "5.7.0.202003110725-r"
    val scalatest = "3.1.1"
    val scalacheck = "1.14.1"
    object cats {
      val core = "2.1.1"
      val effect = "2.1.3"
    }
    object circe {
      val core = "0.13.0"

      object cbor {
        val bullet = "1.5.0"
      }

    }
  }

  object Library {
    val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % Version.jgit
    val scalatest = "org.scalactic" %% "scalactic" % Version.scalatest % Test
    val scalacheck = "org.scalacheck" %% "scalacheck" % Version.scalacheck % Test
    object cats {
      val core = "org.typelevel" %% "cats-core" % Version.cats.core
      val laws = "org.typelevel" %% "cats-effect-laws" % "2.1.3" % Test
      val effect = "org.typelevel" %% "cats-effect" % Version.cats.effect
    }

    object circe {
      val all = Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
      ).map(_ % Version.circe.core)

      object cbor {
        val essentials = Seq(
          "io.bullet" %% "borer-core",
          "io.bullet" %% "borer-compat-circe"
        ).map(_ % Version.circe.cbor.bullet)
      }
    }
  }

  object Module {
    import Library._
    val protocol = Seq(cats.core, cats.laws, cats.effect, scalatest, scalacheck)
    val database = Seq(jgit, scalatest, scalacheck, cats.effect) ++ circe.all ++ circe.cbor.essentials
  }
}
