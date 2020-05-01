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
  }

  object Module {
    import Library._
    val protocol = Seq(cats.core, cats.laws, scalatest, scalacheck)
    val database = Seq(jgit, scalatest, scalacheck, cats.effect)
  }
}
