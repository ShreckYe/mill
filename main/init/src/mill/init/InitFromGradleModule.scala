package mill.init

import mill.define.{Discover, ExternalModule}
import mill.{T, TaskModule}

@mill.api.experimental
object InitFromGradleModule extends ExternalModule with InitFromGradleModule with TaskModule {
  lazy val millDiscover: Discover = Discover[this.type]
}

/**
 * Defines a [[InitModule.init task]] to convert a Gradle build to Mill.
 */
@mill.api.experimental
trait InitFromGradleModule extends InitFromAnotherBuildToolModule {
  override def buildGenClasspathArtifact: String = "mill-main-gradle"
  override def buildGenMainClass: T[String] = "mill.main.gradle.BuildGen"
}
