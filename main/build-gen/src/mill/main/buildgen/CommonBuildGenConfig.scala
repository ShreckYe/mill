package mill.main.buildgen

import mainargs.{Flag, arg}

trait CommonBuildGenConfig {
  @arg(doc = "name of generated base module trait defining project metadata settings")
  def baseModule: Option[String] = None
  @arg(doc = "name of generated nested test module")
  def testModule: String = "test"
  @arg(doc = "name of generated companion object defining constants for dependencies")
  def depsObject: Option[String] = None
  @arg(doc = "capture properties defined for publishing")
  def publishProperties: Flag = Flag()
  @arg(doc = "merge build files generated for a multi-module build")
  def merge: Flag = Flag()
}
