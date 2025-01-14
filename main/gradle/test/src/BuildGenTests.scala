import mill.main.buildgen.TestBuildGen
import mill.main.gradle.BuildGen
import os.Path
import utest.{TestSuite, Tests, assert, test}

object BuildGenTests extends TestSuite with TestBuildGen {
  override def buildFilesSkip(root: Path, path: Path): Boolean = {
    val lastSeg = path.last
    (lastSeg == ".gradle" || lastSeg == ".kotlin.sessions") ||
    (lastSeg == "build" && !path.relativeTo(root).segments.contains("src"))
  }

  override def tests: Tests = Tests {
    val buildChecker = new BuildChecker(BuildGen.main)
    import buildChecker.checkBuild

    // TODO copied from the Maven module and not thoroughly adapted yet

    test("gradle-sample") {
      val sourceRoot = os.sub / "gradle-sample"
      val expectedRoot = os.sub / "expected/gradle-sample"
      assert(
        checkBuild(sourceRoot, expectedRoot)
      )
    }

    test("config") {
      test("all") {
        val sourceRoot = os.sub / "gradle-sample"
        val expectedRoot = os.sub / "expected/config/all/gradle-sample"
        assert(
          checkBuild(
            sourceRoot,
            expectedRoot,
            "--base-module",
            "MyModule",
            "--test-module",
            "tests",
            "--deps-object",
            "Deps",
            "--publish-properties",
            "--merge",
            "--cache-repository",
            "--process-plugins"
          )
        )
      }
    }
  }
}
