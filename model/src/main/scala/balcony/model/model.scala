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
  case object Failure extends Result
}

case class CodeCommit(value: Commit)

case class Commit(value: Hash)
case class Hash(value: String)

case class BuildScript(
  location: URI,
  name: String,
  reference: Hash,
  environment: Environment
)

sealed trait Artifact
case class LocalArtifact(location: Path) extends Artifact

case class RemoteArtifact(
  location: URI
) extends Artifact

case class BuildOutput(
  reference: Hash,
  artifact: Artifact
)

case class Environment(reference: Hash)

case class EnvironmentBuild(
  build: Build,
  output: BuildOutput,
  environment: Environment,
  buildScriptReference: Hash
)