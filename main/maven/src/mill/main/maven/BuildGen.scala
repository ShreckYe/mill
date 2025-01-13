package mill.main.maven

import mainargs.ParserForClass
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
  override def configParser = ParserForClass[BuildGenConfig]
  override def getMavenNodeTree(workspace: Path, cfg: BuildGenConfig) = {
    val modeler = Modeler(cfg)
    Tree.from(Seq.empty[String]) { dirs =>
      val model = modeler(workspace / dirs)
      (Node(dirs, model), model.getModules.iterator().asScala.map(dirs :+ _))
    }
  }
}
