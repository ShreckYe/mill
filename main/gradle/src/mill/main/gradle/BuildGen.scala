package mill.main.gradle

import mainargs.{Flag, ParserForClass, arg, main}
import mill.main.buildgen.*
import mill.main.maven.{CommonMavenPomBuildGen, Modeler}
import org.apache.commons.lang3.StringUtils
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.model.{GradleProject, GradleTask}
import os.Path

import java.io.File
import java.net.URI
import scala.jdk.CollectionConverters.{IterableHasAsJava, IterableHasAsScala}
import scala.reflect.io.Path.jfile2path

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
object BuildGen extends CommonMavenPomBuildGen[BuildGenConfig] {
  override def configParser = ParserForClass[BuildGenConfig]

  override def originalBuildToolName = "Gradle"

  private val fallbackGeneratePomFileTaskName =
    "generatePomFileForFallbackMavenForMillInitPublication"
  private val generatePomFileTaskNamePattern = "generatePomFileFor(.+)Publication".r

  override def getMavenNodeTree(workspace: Path, config: BuildGenConfig): Tree[MavenNode] = {
    val newConnector = GradleConnector.newConnector()

    val connector1 = config.useInstallation.fold(newConnector)(newConnector.useInstallation)
    val connector2 = config.useGradleVersion.fold(connector1)(connector1.useGradleVersion)
    val connector3 = config.useDistribution.fold(connector2)(connector2.useDistribution)
    val connector4 =
      if (config.useBuildDistribution.value) connector3.useBuildDistribution() else connector3
    val connector = config.useGradleUserHomeDir.fold(connector4)(connector4.useGradleUserHomeDir)

    val connection = connector.connect()
    try {
      val project = connection.getModel(classOf[GradleProject])
      val projectAndTaskTree = Tree.from(project)(step =>
        (
          (
            step, {
              val generatePomFileTasks = step.getTasks.asScala.to(LazyList)
                .filter(task => generatePomFileTaskNamePattern.matches(task.getName))

              val (fallbackTasks, nonFallbackTasks) =
                generatePomFileTasks.partition(_.getName == fallbackGeneratePomFileTaskName)

              config.mavenPublicationName
                .fold(nonFallbackTasks.headOption)(name =>
                  nonFallbackTasks.find(
                    _.getName == s"generatePomFileFor${name.capitalize}Publication"
                  )
                )
                .getOrElse(fallbackTasks.head)
            }
          ),
          step.getChildren.asScala
        )
      )

      connection.newBuild()
        .withArguments(
          "--init-script",
          getClass.getResource("init.gradle.kts").toString
        ) // TODO this might not work
        .forTasks(projectAndTaskTree.to[Iterable[(GradleProject, GradleTask)]].map(_._2).asJava)
        .run()

      val modeler = Modeler(config)
      projectAndTaskTree.map({
        case (project, task) =>
          val generatePomFileTaskNamePattern(capitalizedPublicationName) = task.getName
          val possiblePublicationNames =
            Seq(StringUtils.uncapitalize(capitalizedPublicationName), capitalizedPublicationName)
          /* There is a niche case not handled here that the specified publication name in the config is capitalized,
          but the actual one defined is uncapitalized. */
          val pomPath = possiblePublicationNames.map(
            project.getBuildDirectory / "publications" / _ / "pom-default.xml"
          )
            .find(_.exists)
            .get

          Node(
            os.Path(project.getProjectDirectory).relativeTo(workspace).segments,
            modeler.build(pomPath.jfile)
          )
      })
    } finally {
      connection.close()
    }
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
    useGradleUserHomeDir: Option[File],
    @arg(doc =
      "the Maven publication name (set with the `maven-publish` Gradle plugin) to generate the Maven pom.xml to init the Mill project with\n" +
        "You can specify this if there are multiple Maven publications for a project. " +
        "If you do not specify this argument, the first Maven publication will be used. " +
        "If there are no defined Maven publications in the Gradle project, an added default one with no metadata will be used. " +
        "If a publication name is specified but none is found, it fallbacks to the default behavior with this value unspecified."
    )
    mavenPublicationName: Option[String] = None
) extends mill.main.maven.BuildGenConfig
