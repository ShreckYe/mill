package mill.integration

import mill.constants.Util
import mill.integration.testMillInit
import utest.*

private def bumpSbtTo1107(workspacePath: os.Path) =
  // bump sbt version to resolve compatibility issues with lower sbt versions and higher JDK versions
  os.write.over(workspacePath / "project" / "build.properties", "sbt.version = 1.10.7")

// relatively small libraries

private val scalaPlatforms = Seq("js", "jvm", "native")

object MillInitSbtLibraryExampleTests extends BuildGenTestSuite {
  def tests: Tests = Tests {
    /*
    - 21 KB
    - sbt 1.5.2
     */
    val url = "https://github.com/scalacenter/library-example/archive/refs/tags/v1.0.1.zip"

    test - integrationTest(url)(
      _.testMillInit(
        expectedCompileTasks = Some(SplitResolvedTasks(Seq("compile"), Seq())),
        expectedTestTasks = None // scalaprops not supported in `TestModule`
      )
    )
  }
}

object MillInitSbtScalaCsv200Tests extends BuildGenTestSuite {
  def tests: Tests = Tests {
    /*
    - 34 KB
    - originally sbt 1.10.0
     */
    val url = "https://github.com/tototoshi/scala-csv/archive/refs/tags/2.0.0.zip"

    test - integrationTest(url) { tester =>
      bumpSbtTo1107(tester.workspacePath)

      // Cross builds are not supported yet.
      tester.testMillInit(
        expectedCompileTasks = Some(SplitResolvedTasks(Seq(), Seq("compile", "test.compile"))),
        expectedTestTasks = Some(SplitResolvedTasks(Seq(), Seq("test")))
      )
    }
  }
}

object MillInitSbtScalaCsv136Tests extends BuildGenTestSuite {
  def tests: Tests = Tests {
    /*
    - 28 KB
    - originally sbt 1.2.8
     */
    val url = "https://github.com/tototoshi/scala-csv/archive/refs/tags/1.3.6.zip"

    test - integrationTest(url) { tester =>
      bumpSbtTo1107(tester.workspacePath)

      tester.testMillInit(
        expectedCompileTasks = Some(SplitResolvedTasks(
          Seq("compile", "test.compile"),
          Seq.empty
        )),
        expectedTestTasks = Some(SplitResolvedTasks(
          Seq(),
          /*
          Paths relative to the workspace are used in the test sources such as `new File("src/test/resources/simple.csv")`
          and they seem to cause the test to fail with Mill:
          ```text
          java.io.FileNotFoundException: src/test/resources/simple.csv (No such file or directory)
          ```
           */
          Seq("test")
        ))
      )
    }
  }
}

// same as the one in the unit tests
object MillInitSbtMultiProjectExampleTests extends BuildGenTestSuite {
  def tests: Tests = Tests {
    /*
    - 12 KB
    - originally sbt 1.0.2
     */
    val url =
      "https://github.com/pbassiner/sbt-multi-project-example/archive/152b31df9837115b183576b0080628b43c505389.zip"

    test - integrationTest(url) { tester =>
      bumpSbtTo1107(tester.workspacePath)

      val submodules = Seq("common", "multi1", "multi2")
      if (System.getProperty("java.version").split('.').head.toInt <= 11)
        tester.testMillInit(
          expectedCompileTasks = Some(SplitResolvedTasks(
            Seq("compile") ++ submodules.flatMap(_.allCompileTasks),
            Seq.empty
          )),
          expectedTestTasks = Some(SplitResolvedTasks(submodules.map(_.testTask), Seq.empty))
        )
      else
        tester.testMillInit(
          // initCommand = defaultInitCommand ++ Seq("--jvm-id", "11"),
          expectedCompileTasks = Some({
            /*
            `multi1.compile` doesn't work well when Mill is run with JDK 17 and 21:
            ```text
            1 tasks failed
            multi1.compile java.io.IOError: java.lang.RuntimeException: /packages cannot be represented as URI
                java.base/jdk.internal.jrtfs.JrtPath.toUri(JrtPath.java:175)
                scala.tools.nsc.classpath.JrtClassPath.asURLs(DirectoryClassPath.scala:183)
                ...
            ```
            Passing a `jvmId` 11 doesn't work here.
             */
            val succeededSubmoduleCompileTasks = Seq("common.compile", "multi2.compile")
            SplitResolvedTasks(
              Seq("compile") ++ succeededSubmoduleCompileTasks,
              (submodules.flatMap(_.allCompileTasks).toSet -- succeededSubmoduleCompileTasks).toSeq
            )
          }),
          expectedTestTasks = Some(SplitResolvedTasks(Seq.empty, submodules.map(_.testTask)))
        )
    }
  }
}

// relatively large libraries

object MillInitSbtZioHttpTests extends BuildGenTestSuite {
  def tests: Tests = Tests {
    /*
    - 1.4 MB
    - originally sbt 1.10.0
     */
    val url = "https://github.com/zio/zio-http/archive/refs/tags/v3.0.1.zip"

    test - integrationTest(url) { tester =>
      bumpSbtTo1107(tester.workspacePath)

      object submodules {
        val withTests = Seq(
          "sbt-zio-http-grpc-tests",
          "zio-http-cli",
          "zio-http-gen",
          "zio-http-htmx",
          "zio-http-testkit",
          "zio-http.js",
          "zio-http.jvm"
        )
        val withoutTests = Seq(
          "sbt-zio-http-grpc",
          "zio-http-benchmarks",
          "zio-http-docs",
          "zio-http-example",
          "zio-http-tools"
        )
      }

      /*
      The sources shared among multiple platforms (JVM and Scala.js) in "zio-http/shared" in cross-builds
      are not supported in conversion yet,
      causing all dependent project's `compile` tasks to fail.
       */
      tester.testMillInit(
        expectedCompileTasks =
          Some(SplitResolvedTasks(
            Seq("compile"),
            submodules.withTests.flatMap(_.allCompileTasks) ++
              submodules.withoutTests.map(_.compileTask)
          )),
        expectedTestTasks =
          Some(SplitResolvedTasks(Seq(), submodules.withTests.map(_.testTask)))
      )
    }
  }
}

// Scala.js and scala-native projects are not properly imported
object MillInitSbtScalazTests extends BuildGenTestSuite {
  def tests: Tests = Tests {
    /*
    - 0.8 MB
    - sbt 1.9.7
     */
    val url = "https://github.com/scalaz/scalaz/archive/refs/tags/v7.3.8.zip"

    test - integrationTest(url) { tester =>
      import tester.*

      if (!Util.isWindows)
        os.call(("chmod", "+x", "sbt"), cwd = workspacePath)

      val crossDirs = Seq("core", "effect", "example", "iteratee", "scalacheck-binding", "tests")
      val crossSubmodules = crossDirs.flatMap(dir => scalaPlatforms.map(name => s"$dir.$name"))
      val rootModules = Seq("rootJS", "rootJVM", "rootNative")

      tester.testMillInit(
        expectedCompileTasks =
          Some(SplitResolvedTasks(
            /*
            These modules are converted from sbt's aggregated projects without sources or dependencies,
            therefore, their no-op `compile` tasks succeed.
             */
            Seq("compile") ++ rootModules.map(_.compileTask),
            /*
            Common sources shared among multiple platforms (JVM, Scala.js, and Scala Native) in directories such as "core/src"
            are not defined as sbt projects and therefore not converted.
            This leads to modules such as "core/jvm" not compiling as common definitions are not found.
             */
            crossSubmodules.map(_.compileTask)
          )),
        // Scalaz uses ScalaCheck which is not supported in conversion yet.
        expectedTestTasks = None
      )
    }
  }
}

// Scala.js and scala-native projects are not properly imported
object MillInitSbtCatsTests extends BuildGenTestSuite {
  def tests: Tests = Tests {
    /*
    - 1.9 MB
    - sbt 1.10.7
    - MUnit
     */
    val url = "https://github.com/typelevel/cats/archive/refs/tags/v2.13.0.zip"

    test - integrationTest(url) { tester =>
      val sbtCrossProjects = Seq(
        "algebra-laws",
        "alleycats-laws",
        "kernel-laws",
        "tests"
      )
      /*
      These sbt cross projects have `CrossType.Pure` set,
      so there platform projects have directories named ".js", ".jvm", and ".native",
      and such modules starting with "." are not properly recognized by Mill
       */
      val sbtCrossProjectsWithCrossTypePure =
        Seq("algebra-core", "alleycats-core", "core", "free", "kernel", "laws", "testkit")

      assert((sbtCrossProjects intersect sbtCrossProjectsWithCrossTypePure).isEmpty)

      val nonCrossModules = Seq("bench", "binCompatTest", "site", "unidocs")
      val resolvedTestModules = (scalaPlatforms.map(name => s"alleycats-laws.$name") ++
        Seq(
          "binCompatTest",
          // "tests.jvm" and "tests.native" don't have their own test sources, so their test modules are not converted.
          "tests.js"
        ))
        .map(module => s"$module.test")
      val submoduleCompileTasks = (sbtCrossProjects.flatMap(project =>
        scalaPlatforms.map(platform => s"$project.$platform")
      ) ++
        nonCrossModules ++
        resolvedTestModules)
        .map(module => s"$module.compile")

      tester.testMillInit(
        expectedCompileTasks = Some({
          val succeededSubmoduleCompileTasks =
            Seq("tests.js.compile", "tests.jvm.compile", "unidocs.compile")
          SplitResolvedTasks(
            Seq("compile") ++ succeededSubmoduleCompileTasks,
            submoduleCompileTasks diff succeededSubmoduleCompileTasks
          )
        }),
        expectedTestTasks = Some(SplitResolvedTasks(Seq(), resolvedTestModules))
      )
    }
  }
}

// Converting child projects nested in a parent directory which is not a project is not supported yet.
object MillInitSbtPlayFrameworkTests extends BuildGenTestSuite {
  def tests: Tests = Tests {
    // Commented out as it causes `java.util.concurrent.TimeoutException: Future timed out after [600000 milliseconds]` in the CI.
    /*
    /*
    - 4.8 MB
    - sbt 1.10.5
     */
    val url = "https://github.com/playframework/playframework/archive/refs/tags/3.0.6.zip"

    test - integrationTest(url) { tester =>
      import tester.*

      val initResult = eval(defaultInitCommand, stdout = os.Inherit, stderr = os.Inherit)
      assert(initResult.isSuccess)

      val compileResult = eval("compile")
      assert(compileResult.isSuccess)
    }
     */
  }
}

// Scala.js and scala-native projects are not properly imported
object MillInitSbtScalaCheckTests extends BuildGenTestSuite {
  def tests: Tests = Tests {
    /*
    - 245 KB
    - originally sbt 1.10.1
     */
    val url = "https://github.com/typelevel/scalacheck/archive/refs/tags/v1.18.1.zip"

    test - integrationTest(url) { tester =>
      import tester.*

      bumpSbtTo1107(workspacePath)

      val initResult = eval(defaultInitCommand, stdout = os.Inherit, stderr = os.Inherit)
      assert(initResult.isSuccess)

      val compileResult = eval("compile")
      assert(compileResult.isSuccess)
    }
  }
}
