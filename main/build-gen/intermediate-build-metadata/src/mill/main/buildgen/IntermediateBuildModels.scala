package mill.main.buildgen

import scala.collection.immutable.SortedSet

@mill.api.internal
object IntermediateBuildModels {

  /**
   * An intermediate build metadata representation for all kinds of build tools which will be imported into Mill.
   */
  // TODO publish
  case class Build(
      packaging: String,
      // sourcePath: Path
      hasMultiplePackages: Boolean, // TODO consider removing this
      allDependencies: AllDependencies,
      testFramework: Option[String],
      publishMetadata: Nothing,
      javacOptions: Nothing
  )

  case class Exclusion(groupId: String, artifactId: String)
  case class ExternalDependency(
      groupId: String,
      artifactId: String,
      version: String,
      `type`: Option[String],
      classifier: Option[String],
      exclusions: Seq[Exclusion]
  )

  case class ScopeDependencies(
      externalDependencies: SortedSet[ExternalDependency],
      moduleDependencies: SortedSet[String]
  )

  type AllDependencies = GenericBuildModels.AllDependencies[ScopeDependencies]
}
