package mill.main.buildgen

import os.Path

@mill.api.internal
trait CommonBuildGen[Config <: CommonBuildGenConfig] {
  // TODO make members protected?

  type MillNode = Node[BuildObject]

  def originalBuildToolName: String
  def generateMillNodeTree(workspace: Path, cfg: Config): Tree[MillNode]
}
