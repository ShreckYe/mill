package mill.main.buildgen

import mainargs.{Flag, arg}

@mill.api.internal
trait CommonBuildGenConfig {
  @arg(doc = "name of generated base module trait defining project metadata settings")
  def baseModule: Option[String]
  @arg(doc = "name of generated nested test module")
  def testModule: String
  @arg(doc = "name of generated companion object defining constants for dependencies")
  def depsObject: Option[String]
  @arg(doc = "capture properties defined for publishing")
  def publishProperties: Flag
  @arg(doc = "merge build files generated for a multi-module build")
  def merge: Flag
}
