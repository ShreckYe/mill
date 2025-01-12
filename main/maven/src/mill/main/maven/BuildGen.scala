package mill.main.maven

import mainargs.{Flag, arg, main}
import mill.main.buildgen.*
import os.Path

import scala.jdk.CollectionConverters.*

/**
 * Converts a Maven build to Mill by generating Mill build file(s) from POM file(s).
 *
 * The generated output should be considered scaffolding and will likely require edits to complete conversion.
 *
 * ===Capabilities===
 * The conversion
 *  - handles deeply nested modules
 *  - captures project metadata
 *  - configures dependencies for scopes:
 *    - compile
 *    - provided
 *    - runtime
 *    - test
 *  - configures testing frameworks:
 *    - JUnit 4
 *    - JUnit 5
 *    - TestNG
 *  - configures multiple, compile and test, resource directories
 *
 * ===Limitations===
 * The conversion does not support:
 *  - plugins, other than maven-compiler-plugin
 *  - packaging, other than jar, pom
 *  - build extensions
 *  - build profiles
 */
@mill.api.internal
object BuildGen extends CommonMavenPomBuildGen[BuildGenConfig] {
  override def getMavenNodeTree(workspace: Path, cfg: BuildGenConfig) = {
    val modeler = Modeler(cfg)
    Tree.from(Seq.empty[String]) { dirs =>
      val model = modeler(workspace / dirs)
      (Node(dirs, model), model.getModules.iterator().asScala.map(dirs :+ _))
    }
  }
}

// TODO move to `CommonMavenPomBuildGen.scala`?
@main
@mill.api.internal
case class BuildGenConfig(
    override val baseModule: Option[String] = None,
    override val testModule: String = "test",
    override val depsObject: Option[String] = None,
    // This message is different from the common one.
    @arg(doc = "capture properties defined in pom.xml for publishing")
    override val publishProperties: Flag = Flag(),
    override val merge: Flag = Flag(),
    @arg(doc = "use cache for Maven repository system")
    cacheRepository: Flag = Flag(),
    @arg(doc = "process Maven plugin executions and configurations")
    processPlugins: Flag = Flag()
) extends CommonBuildGenConfig with ModelerConfig
