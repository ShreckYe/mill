package mill.main.buildgen

import mill.T
import mill.api.PathRef
import mill.main.client.OutFiles
import mill.scalalib.scalafmt.ScalafmtModule
import mill.testkit.{TestBaseModule, UnitTester}
import utest.framework.TestPath

import java.nio.file.FileSystems

object TestBuildGen {
  // Change this to true to update test data on disk
  def updateSnapshots = true // TODO

  class BuildChecker(main: Array[String] => Unit) {
    val resources = os.Path(sys.env("MILL_TEST_RESOURCE_DIR"))
    val scalafmtConfigFile = PathRef(resources / ".scalafmt.conf")

    def checkBuild(
        sourceRel: os.SubPath,
        expectedRel: os.SubPath,
        args: String*
    )(implicit tp: TestPath): Boolean = {
      // prep
      val dest = os.pwd / tp.value
      os.copy.over(resources / sourceRel, dest, createFolders = true, replaceExisting = true)

      // gen
      os.dynamicPwd.withValue(dest)(main(args.toArray))

      // fmt
      val files = buildFiles(dest)
      object module extends TestBaseModule with ScalafmtModule {
        override protected def filesToFormat(sources: Seq[PathRef]): Seq[PathRef] = files
        override def scalafmtConfig: T[Seq[PathRef]] = Seq(scalafmtConfigFile)
      }
      val eval = UnitTester(module, dest)
      eval(module.reformat())

      // test
      checkFiles(files.map(_.path.relativeTo(dest).asSubPath), dest, resources / expectedRel)
    }
  }

  def buildFiles(root: os.Path): Seq[PathRef] =
    os.walk.stream(root, skip = (root / "out").equals)
      .filter(_.ext == "mill")
      .map(PathRef(_))
      .toSeq

  def checkFiles(actualFiles: Seq[os.SubPath], root: os.Path, expectedRoot: os.Path): Boolean = {
    // Non *.mill files, that are not in test data, that we don't want
    // to see in the diff
    val toCleanUp = os.walk(root, skip = _.startsWith(root / OutFiles.defaultOut))
      .filter(os.isFile)
      .filter(!_.lastOpt.exists(_.endsWith(".mill")))
    toCleanUp.foreach(os.remove)

    // Try to normalize permissions while not touching those of committed test data
    val supportsPerms = FileSystems.getDefault.supportedFileAttributeViews().contains("posix")
    if (supportsPerms)
      for {
        testFile <- os.walk(expectedRoot)
        if os.isFile(testFile)
        targetFile = root / testFile.relativeTo(expectedRoot).asSubPath
        if os.isFile(targetFile)
      }
        os.perms.set(targetFile, os.perms(testFile))

    val diffExitCode = os.proc("git", "diff", "--no-index", expectedRoot, root)
      .call(stdin = os.Inherit, stdout = os.Inherit, check = !updateSnapshots)
      .exitCode

    if (updateSnapshots && diffExitCode != 0) {
      System.err.println(
        s"Expected and actual files differ, updating expected files in resources under $expectedRoot"
      )

      os.remove.all(expectedRoot)
      os.copy(root, expectedRoot)
    }

    diffExitCode == 0 || updateSnapshots
  }
}
