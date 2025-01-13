import mill.main.buildgen.TestBuildGen
import mill.main.gradle.BuildGen
import utest.{TestSuite, Tests, assert, test}

object BuildGenTests extends TestSuite {
  override def tests: Tests = Tests {
    val buildChecker = new TestBuildGen.BuildChecker(BuildGen.main)
    import buildChecker.checkBuild

    // TODO copied from the Maven module and not thoroughly adapted yet

    test("gradle-sample") {
      val sourceRoot = os.sub / "gradle-sample"
      val expectedRoot = os.sub / "expected/gradle-sample"
      assert(
        checkBuild(sourceRoot, expectedRoot)
      )
    }

    /*
    test("config") {
      test("all") {
        val sourceRoot = os.sub / "gradle-samples"
        val expectedRoot = os.sub / "expected/config/all"
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
     */
  }
}
