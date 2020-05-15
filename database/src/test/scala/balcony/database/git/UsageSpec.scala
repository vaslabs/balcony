package balcony.database.git

import balcony.database.git.CiTracker.Command
import org.scalacheck.{Prop, Properties}

class UsageSpec extends Properties("storage") {

   property("run-build-on-last-commit") = Prop.forAll(RuntimeDependencies.gen) { runtimeDependencies: RuntimeDependencies =>
     val ciBuilder = CiTracker.create(runtimeDependencies.git)
     val environmentBuildIO = ciBuilder.apply(
       Command.BuildLatestCommit(runtimeDependencies.buildScript)
     )
     environmentBuildIO.unsafeRunSync().build.codeCommit.value == runtimeDependencies.commits.last
   }

}


