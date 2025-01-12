package mill.main.buildgen

import mainargs.ParserForClass
import os.Path

@mill.api.internal
trait CommonBuildGen[Config <: CommonBuildGenConfig] {
  // TODO make members protected?

  def main(args: Array[String]): Unit = {
    val cfg = ParserForClass[Config].constructOrExit(args.toSeq)
    run(cfg)
  }

  type MillNode = Node[BuildObject]

  def originalBuildToolName: String
  def generateMillNodeTree(workspace: Path, cfg: Config): Tree[MillNode]

  private def run(cfg: Config): Unit = {
    val workspace = os.pwd

    println(s"converting $originalBuildToolName build")

    var output: Tree[MillNode] = generateMillNodeTree(workspace, cfg)

    if (cfg.merge.value) {
      println("compacting Mill build tree")
      output = output.merge
    }

    val nodes = output.toSeq
    println(s"generated ${nodes.length} Mill build file(s)")

    println("removing existing Mill build files")
    os.walk.stream(workspace, skip = (workspace / "out").equals)
      .filter(_.ext == ".mill")
      .foreach(os.remove.apply)

    nodes.foreach { node =>
      val file = node.file
      val source = node.source
      println(s"writing Mill build file to $file")
      os.write(workspace / file, source)
    }

    println(s"converted $originalBuildToolName build to Mill")
  }
}
