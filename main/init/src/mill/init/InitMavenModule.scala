package mill.init

import mill.T
import mill.define.{Discover, ExternalModule, TaskModule}

// TODO rename to `InitFromMavenModule`?

@mill.api.experimental
object InitMavenModule extends ExternalModule with InitMavenModule with TaskModule {
  lazy val millDiscover: Discover = Discover[this.type]
}

/**
 * Defines a [[InitModule.init task]] to convert a Maven build to Mill.
 */
@mill.api.experimental
trait InitMavenModule extends InitFromAnotherBuildToolModule {
  override def buildGenClasspathArtifact: String = "mill-main-maven"
  override def buildGenMainClass: T[String] = "mill.main.maven.BuildGen"
}
