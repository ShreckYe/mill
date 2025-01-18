package mill.main.buildgen

import mainargs.ParserForClass
import os.Path

@mill.api.internal
trait CommonBuildGen[Config <: CommonBuildGenConfig] {
  // TODO make members protected?

  def configParser : ParserForClass[Config]

  def main(args: Array[String]): Unit =  {
    val config = configParser.constructOrExit(args.toSeq)
    run(config)
  }

  type MillCodeNode = Node[BuildCodeObject]
  type MillCodeTree = Tree[MillCodeNode]

  def originalBuildToolName: String
  def generateMillCodeTree(workspace: Path, config: Config): MillCodeTree

  private def run(config: Config): Unit = {
    val workspace = os.pwd

    println(s"converting $originalBuildToolName build")

    var output: MillCodeTree = generateMillCodeTree(workspace, config)

    if (config.merge.value) {
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
