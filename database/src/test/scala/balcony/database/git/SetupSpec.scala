package balcony.database.git

import balcony.model.{Build, BuildConfiguration, Commit}
import cats.effect.IO
import org.scalacheck.{Prop, Properties}

class SetupSpec extends Properties("ci-tracking-setup") {

  property("register-build") = Prop.forAll(BuildScriptSetup.gen, RuntimeDependencies.gen()) { (buildScriptSetup, runtimeDependencies) =>
    val ciConfiguration = CiConfiguration.setUp(runtimeDependencies.git).unsafeRunSync()

    val buildHash = ciConfiguration.writeBuild(buildScriptSetup.name, buildScriptSetup.lines)
      .handleErrorWith(t => IO(t.printStackTrace()) *> IO.raiseError(t))
      .unsafeRunSync()

    val allBuilds = ciConfiguration.listBuilds().unsafeRunSync()
    val expected = BuildConfiguration(
        buildScriptSetup.name, Commit(buildHash),
      )

    allBuilds.contains(expected)
  }

}
