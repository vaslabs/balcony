package balcony.database.git

import balcony.database.git.CiTracker.Command
import balcony.model.EnvironmentBuild
import cats.effect.IO
import org.scalacheck.{Prop, Properties}

class BuildLatestSpec extends Properties("storage") {

   property("run-build-on-last-commit") = Prop.forAll(RuntimeDependencies.gen) { runtimeDependencies: RuntimeDependencies =>
     val ciTracker = CiTracker.create(runtimeDependencies.git)
     val environmentBuildIO = ciTracker.apply(
       Command.BuildLatestCommit(runtimeDependencies.buildScript)
     )
     val build = environmentBuildIO.handleErrorWith(
       t => IO(t.printStackTrace()) *> IO.raiseError(t)
     ).unsafeRunSync().build

     val commandCondition = build.codeCommit.value == runtimeDependencies.commits.last

     val latestBuild: IO[EnvironmentBuild] = ciTracker.apply(Command.LatestBuild()).handleErrorWith(
       t => IO(t.printStackTrace()) *> IO.raiseError(t)
     )
     val execution = latestBuild.unsafeRunSync()
     val queryCondition = execution.build == build

     commandCondition && queryCondition
   }

}


