package mill.main.maven

import mill.main.buildgen.TestBuildGen
import utest.*

object BuildGenTests extends TestSuite {
  def tests: Tests = Tests {
    val buildChecker = new TestBuildGen.BuildChecker(BuildGen.main)
    import buildChecker.checkBuild

    // multi level nested modules
    test("maven-samples") {
      val sourceRoot = os.sub / "maven-samples"
      val expectedRoot = os.sub / "expected/maven-samples"
      assert(
        checkBuild(sourceRoot, expectedRoot)
      )
    }

    test("config") {
      test("all") {
        val sourceRoot = os.sub / "maven-samples"
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

      test("base-module") {
        val sourceRoot = os.sub / "maven-samples/multi-module"
        val expectedRoot = os.sub / "expected/config/base-module"
        assert(
          checkBuild(sourceRoot, expectedRoot, "--base-module", "MyModule")
        )
      }

      test("deps-object") {
        val sourceRoot = os.sub / "config/deps-object"
        val expectedRoot = os.sub / "expected/config/deps-object"
        assert(
          checkBuild(sourceRoot, expectedRoot, "--deps-object", "Deps")
        )
      }

      test("test-module") {
        val sourceRoot = os.sub / "maven-samples/single-module"
        val expectedRoot = os.sub / "expected/config/test-module"
        assert(
          checkBuild(sourceRoot, expectedRoot, "--test-module", "tests")
        )
      }

      test("merge") {
        val sourceRoot = os.sub / "maven-samples"
        val expectedRoot = os.sub / "expected/config/merge"
        assert(
          checkBuild(sourceRoot, expectedRoot, "--merge")
        )
      }

      test("publish-properties") {
        val sourceRoot = os.sub / "maven-samples/single-module"
        val expectedRoot = os.sub / "expected/config/publish-properties"
        assert(
          checkBuild(sourceRoot, expectedRoot, "--publish-properties")
        )
      }
    }

    test("misc") {
      test("custom-resources") {
        val sourceRoot = os.sub / "misc/custom-resources"
        val expectedRoot = os.sub / "expected/misc/custom-resources"
        assert(
          checkBuild(sourceRoot, expectedRoot)
        )
      }
    }
  }
}
