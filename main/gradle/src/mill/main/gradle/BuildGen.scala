package mill.main.gradle

import mainargs.{Flag, ParserForClass, arg, main}
import mill.main.buildgen.*
import mill.main.maven.{CommonMavenPomBuildGen, Modeler}
import org.apache.commons.lang3.StringUtils
import org.gradle.tooling.model.{GradleProject, GradleTask}
import org.gradle.tooling.{ConfigurableLauncher, GradleConnector}
import os.Path

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
 *  - captures project Maven publication metadata
 *  - configures dependencies for configurations for both the main and test source sets:
 *    - `implementation` and `api` (replacements for the deprecated `compile`), similar to Maven's `compile`
 *    - `compileOnly`, similar to Maven's `provided`
 *    - `runtimeOnly`, similar to Maven's `runtime`
 *  - configures testing frameworks:
 *    - JUnit 4
 *    - JUnit 5
 *    - TestNG
 *  - configures multiple, main and test, resource directories
 *  - configures javac options TODO check this
 *
 * ===Limitations===
 * The conversion does not support:
 *  - plugins, other than `java` and `maven-publish`
 *  - packaging, other than pom
 *  - build variants
 */
@mill.api.internal
object BuildGen extends CommonMavenPomBuildGen[BuildGenConfig] {
  override def configParser = ParserForClass[BuildGenConfig]

  override def originalBuildToolName = "Gradle"

  private val fallbackGeneratePomFileTaskName =
    "generatePomFileForFallbackMavenForMillInitPublication"
  private val generatePomFileTaskNamePattern = "generatePomFileFor(.+)Publication".r

  override def getMavenNodeTree(workspace: Path, config: BuildGenConfig): Tree[MavenNode] = {
    /*
    val newConnector = GradleConnector.newConnector()

    val connector1 = config.useInstallation.fold(newConnector)(newConnector.useInstallation)
    val connector2 = config.useGradleVersion.fold(connector1)(connector1.useGradleVersion)
    val connector3 = config.useDistribution.fold(connector2)(connector2.useDistribution)
    val connector4 =
      if (config.useBuildDistribution.value) connector3.useBuildDistribution() else connector3
    val connector = config.useGradleUserHomeDir.fold(connector4)(connector4.useGradleUserHomeDir)
     */
    val connector = GradleConnector.newConnector()

    println("connecting to the Gradle project")
    val connection = connector.forProjectDirectory(workspace.toIO).connect()
    println("connected")
    try {
      implicit class ConfigurableLauncherExt[T <: ConfigurableLauncher[T]](
          val configurableLauncher: ConfigurableLauncher[T]
      ) {
        def withInitScriptArguments: T =
          configurableLauncher.withArguments(
            "--init-script",
            // TODO this might not work when built into a jar
            getClass.getResource("init.gradle.kts").toString
          )
      }

      val project = connection.model(classOf[GradleProject]).withInitScriptArguments.get()
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
      println(s"subprojects and their \"generatePomFile\" tasks retrieved: $projectAndTaskTree")

      println("running \"generatePomFile\" tasks with a custom init script")

      connection.newBuild().withInitScriptArguments
        .forTasks(projectAndTaskTree.to[Iterable[(GradleProject, GradleTask)]].map(_._2).asJava)
        .run()

      println("feeding the generated POM files to Maven POM build generation")
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
    /*
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
    @arg(doc = "which Gradle distribution to use in the `GradleConnector`")
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
     */
    @arg(doc =
      "the Maven publication name (set with the `maven-publish` Gradle plugin) to generate the Maven POM file to init the Mill project with\n" +
        "You can specify this if there are multiple Maven publications for a project. " +
        "If you do not specify this argument, the first Maven publication will be used. " +
        "If there are no defined Maven publications in the Gradle project, an added default one with no metadata will be used. " +
        "If a publication name is specified but none is found, it fallbacks to the default behavior with this value unspecified."
    )
    mavenPublicationName: Option[String] = None,
    override val baseModule: Option[String] = None,
    override val testModule: String = "test",
    override val depsObject: Option[String] = None,
    override val publishProperties: Flag = Flag(),
    override val merge: Flag = Flag(),
    override val cacheRepository: Flag = Flag(),
    override val processPlugins: Flag = Flag()
) extends mill.main.maven.CommonMavenPomBuildGenConfig
