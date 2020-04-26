# Balcony

Balcony is an all in one CLI tool for CI/CD integration tightly coupled with the codebase.

## Goals

1. Full history of build results is tracked alongside the code version. For instance:

```scala
case class Code(commit: Hash, builds: Map[Environment, Build])
case class Build(user: User, outcome: BuildOutcome, artifacts: Set[Artifact], buildCommit: Hash)

sealed trait Artifact
case class LocalArtifact(location: URI)
case class RemoteArtifact(location: URI, credentials: Option[Credentials])

case class Environment(identifier: String)
```

2. Build history maintains distribution given that merge conflicts of builds should be extremely rare.
Merge strategy is concatenation which means builds will be unordered.

3. The build history is as portable as the rest of the code that uses GIT as the SCM.

4. The CLI is platform independent.

5. The CLI allows incremental builds given that rules have been specified on how to reuse artifacts from previous builds.

6. Artifacts generated from successful builds on any code state A must be indistinguishable and this is enforced.
For example if code is on commit afe123 and build 0cafed and 1caded were executed and were successful , should generate
artifacts that have the same SHA256 . Otherwise all builds are marked as failed.

7. The CLI recognises environments and can link different libraries or files for each environment. This aims to solve
the `configuration` problem and make it possible to inject configurations using the underlying language.