package balcony.model

import java.net.URI

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

case class BuildConfiguration(name: String, commit: Commit)

case class CodeCommit(value: Commit) {
  def show: String = value.show
}

case class Commit(value: Hash) {
  def show: String = value.show
}
object Commit {
  def fromString(value: String): Commit = Commit(Hash(value))
}
case class Hash(value: String) {
  def show: String = value
}

case class BuildScript(
  repoLocation: String,
  location: String,
  name: String,
  reference: Hash,
  environment: Environment
)

sealed trait Artifact
case class LocalArtifact(location: String) extends Artifact

case class RemoteArtifact(
  location: URI
) extends Artifact

sealed trait BuildOutput

case class BuildArtifacs(
  reference: Hash,
  artifact: Artifact
) extends BuildOutput
case object NoOutput extends BuildOutput

case class Environment(reference: String)

case class EnvironmentBuild(
  build: Build,
  output: BuildOutput,
  environment: Environment,
  buildScriptReference: Hash
)