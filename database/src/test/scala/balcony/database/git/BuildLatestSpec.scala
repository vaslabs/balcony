package balcony.database.git

import balcony.database.git.CiTracker.{Command, MetaQuery, Query}
import balcony.model.EnvironmentBuild
import cats.effect.IO
import org.scalacheck.{Prop, Properties}

class BuildLatestSpec extends Properties("storage") {

   property("run-build-on-last-commit") = Prop.forAll(RuntimeDependencies.gen) { runtimeDependencies: RuntimeDependencies =>
     val ciTracker = CiTracker.create(runtimeDependencies.git)
     val environmentBuildIO = ciTracker.apply(
       Command.BuildLatestCommit(runtimeDependencies.buildScript)
     )
     val build = environmentBuildIO.unsafeRunSync().build

     val commandCondition = build.codeCommit.value == runtimeDependencies.commits.last

     val latestBuild: IO[EnvironmentBuild] = ciTracker.apply(Query.LatestBuild()).handleErrorWith(
       t => IO(t.printStackTrace()) *> IO.raiseError(t)
     )
     val execution = latestBuild.unsafeRunSync()
     val queryCondition = execution.build == build

     commandCondition && queryCondition
   }

  property("recover-logs-of-last-build") = Prop.forAll(RuntimeDependencies.gen.withBuildScript) {
    runtimeDependencies: RuntimeDependencies =>
      val ciTracker = CiTracker.create(runtimeDependencies.git)
      ciTracker.apply(Command.BuildLatestCommit(runtimeDependencies.buildScript)).handleErrorWith {
        t => IO(t.printStackTrace()) *> IO.raiseError(t)
      }.unsafeRunSync()

      val logs = ciTracker.apply(MetaQuery.LatestLogs()).handleErrorWith {
        t => IO(t.printStackTrace()) *> IO.raiseError(t)
      }.unsafeRunSync()
      logs.data == runtimeDependencies.buildLog
  }
}


