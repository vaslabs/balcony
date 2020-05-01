package balcony.model

import java.net.URI
import java.nio.file.Path

case class Build(
  codeCommit: CodeCommit,
  reference: Hash,
  result: Build.Result
)

object Build {
  sealed trait Result
  case object Success extends Result
  case class Failure(errorCode: Int) extends Result
}

case class CodeCommit(value: Commit) {
  def show: String = value.show
}

case class Commit(value: Hash) {
  def show: String = value.show
}
case class Hash(value: String) {
  def show: String = value
}

case class BuildScript(
  location: Path,
  name: String,
  reference: Hash,
  environment: Environment
)

sealed trait Artifact
case class LocalArtifact(location: Path) extends Artifact

case class RemoteArtifact(
  location: URI
) extends Artifact

sealed trait BuildOutput

case class BuildArtifacs(
  reference: Hash,
  artifact: Artifact
) extends BuildOutput
case object NoOutput extends BuildOutput

case class Environment(reference: Hash)

case class EnvironmentBuild(
  build: Build,
  output: BuildOutput,
  environment: Environment,
  buildScriptReference: Hash
)