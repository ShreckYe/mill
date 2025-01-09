package mill.main.gradle

import mainargs.{Flag, ParserForClass, arg, main}
import mill.main.buildgen.CommonBuildGenConfig
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.gradle.GradleBuild

import java.io.File
import java.net.URI

/**
 * Converts a Gradle build to Mill by generating Mill build file(s) with the Gradle Tooling API.
 *
 * The generated output should be considered scaffolding and will likely require edits to complete conversion.
 *
 * ===Capabilities===
 * The conversion
 *  - handles deeply nested modules
 *  - captures project metadata TODO check this
 *  - configures dependencies for configurations for both the main and test source sets:
 *    - implementation, api (replacements for deprecated `compile`)
 *    - compileOnly
 *    - runtimeOnly
 *  - configures testing frameworks:
 *    - JUnit 4
 *    - JUnit 5
 *    - TestNG
 *  - configures multiple, main and test, resource directories
 *
 * ===Limitations===
 * The conversion does not support:
 *  - plugins, other than `java` and `application`
 *  - packaging, other than jar, pom
 *  - build extensions TODO check this
 *  - build profiles TODO check this
 */
@mill.api.internal
object BuildGen {
  def main(args: Array[String]): Unit = {
    val config = ParserForClass[BuildGenConfig].constructOrExit(args.toSeq)
    run(config)
  }

  private def run(config: BuildGenConfig): Unit = {
    val newConnector = GradleConnector.newConnector()

    val connector1 = config.useInstallation.fold(newConnector)(newConnector.useInstallation)
    val connector2 = config.useGradleVersion.fold(connector1)(connector1.useGradleVersion)
    val connector3 = config.useDistribution.fold(connector2)(connector2.useDistribution)
    val connector4 =
      if (config.useBuildDistribution.value) connector3.useBuildDistribution() else connector3
    val connector = config.useGradleUserHomeDir.fold(connector4)(connector4.useGradleUserHomeDir)

    val connection = connector.connect()

    val gradleBuild = connection.getModel(classOf[GradleBuild])

    gradleBuild.getRootProject

    // TODO
  }
}

@main
@mill.api.internal
case class BuildGenConfig(
    /**
     * @see [[GradleConnector.useInstallation]]
     */
    @arg(doc = "Gradle installation to use in the `GradleConnector`")
    useInstallation: Option[File] = None,
    /**
     * @see [[GradleConnector.useGradleVersion]]
     */
    @arg(doc = "Gradle version to use in the `GradleConnector`")
    useGradleVersion: Option[String] = None,
    /**
     * @see [[GradleConnector.useDistribution]]
     */
    @arg(doc = "which Gradle distribution to use to use in the `GradleConnector`")
    useDistribution: Option[URI] = None,
    /**
     * @see [[GradleConnector.useBuildDistribution]]
     */
    @arg(doc =
      "use the Gradle distribution defined by the target Gradle build in the `GradleConnector`"
    )
    useBuildDistribution: Flag = Flag(),
    /**
     * @see [[GradleConnector.useGradleUserHomeDir]]
     */
    @arg(doc = "the user's Gradle home directory to use in the `GradleConnector`")
    useGradleUserHomeDir: Option[File]
) extends CommonBuildGenConfig
