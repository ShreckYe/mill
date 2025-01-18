package mill.main.maven

import mainargs.{Flag, arg}
import mill.main.buildgen.*
import mill.main.buildgen.GenericBuildModels.AllDependencies
import mill.main.buildgen.IntermediateBuildModels.{Exclusion, ExternalDependency, ScopeDependencies}
import mill.runner.FileImportGraph.backtickWrap
import org.apache.maven.model.{Dependency, Model}
import os.Path

import scala.Function.unlift
import scala.collection.immutable.{SortedMap, SortedSet}
import scala.jdk.CollectionConverters.*

trait CommonMavenPomBuildGen[Config <: CommonMavenPomBuildGenConfig]
    extends CommonBuildGen[Config] {
  // TODO make public members protected?

  type MavenNode = Node[Model]

  override def originalBuildToolName = "Maven"

  override def generateMillCodeTree(workspace: Path, config: Config): Tree[MillCodeNode] = {
    val input = getIntermediateTree(workspace, config)

    intermediateTreeToMillCodeTree(input, config)
  }

  def getMavenNodeTree(workspace: Path, config: Config): Tree[MavenNode]

  // TODO remove if not used
  case class CombinedIntermediateModel(
      build: IntermediateBuildModels.Build,
      model: Model // for publish metadata retrieval from the published POM file
  )

  type IntermediateNode = Node[IntermediateBuildModels.Build]
  type IntermediateTree = Tree[IntermediateNode]

  def getIntermediateTree(workspace: Path, config: Config): IntermediateTree = {
    val input = getMavenNodeTree(workspace, config)
    val packages = // for resolving moduleDeps
      input
        .fold(Map.newBuilder[Gav, Package])((z, build) => z += ((Id(build.module), build.pkg)))
        .result()

    input.map { case build @ Node(dirs, model) =>
      val packaging = model.getPackaging

      val allDependencies = ScopeDependenciesInCode.intermediateAllDependencies(model, packages)

      // Maven module can be tests only
      val notPom = "pom" != model.getPackaging
      val testModule = if (notPom)
        model.getDependencies.asScala.collectFirst(unlift(dependency =>
          // Maven module can be tests only
          Option(dependency.getGroupId match {
            case "junit" => "TestModule.Junit4"
            case "org.junit.jupiter" => "TestModule.Junit5"
            case "org.testng" => "TestModule.TestNg"
            case _ => null
          })
        ))
      else None

      build.copy(module =
        IntermediateBuildModels.Build(
          packaging,
          packages.size > 1,
          allDependencies,
          testModule,
          ???,
          ???
        )
      )
    }
  }

  private def intermediateTreeToMillCodeTree(
      input: IntermediateTree,
      config: Config
  ): Tree[MillCodeNode] = {
    val baseModuleTypedef = config.baseModule.fold("") { baseModule =>
      val metadataSettings =
        publishMetadata(??? /*input.node.module.model*/, config) // TODO submodules?

      s"""trait $baseModule extends PublishModule with MavenModule {
         |
         |$metadataSettings
         |}""".stripMargin
    }

    input.map { case build @ Node(dirs, intermediateBuild) =>
      // TODO This is added for the code to compile. Refactor and get rid of it.
      val mavenModel: Model = ???

      val packaging = intermediateBuild.packaging
      val millSourcePath = os.Path(mavenModel.getProjectDirectory) // TODO dirs?

      val imports = {
        val b = SortedSet.newBuilder[String]
        b += "mill._"
        b += "mill.javalib._"
        b += "mill.javalib.publish._"
        if (dirs.nonEmpty) config.baseModule.foreach(baseModule => b += s"$$file.$baseModule")
        else if (intermediateBuild.hasMultiplePackages) b += "$packages._"
        b.result()
      }

      val supertypes = {
        val b = Seq.newBuilder[String]
        b += "RootModule"
        config.baseModule.fold(b += "PublishModule" += "MavenModule")(b += _)
        b.result()
      }

      val ScopeDependenciesInCode.CompanionsAndAllDependencies(
        companions,
        AllDependencies(compileDeps, providedDeps, runtimeDeps, testDeps)
      ) = ScopeDependenciesInCode.toCompanionsAndAllDependenciesInCode(
        intermediateBuild.allDependencies,
        config
      )

      val inner = {
        val javacOptions = Plugins.MavenCompilerPlugin.javacOptions(mavenModel)

        val artifactNameSetting = {
          val id = mavenModel.getArtifactId
          val name = if (dirs.nonEmpty && dirs.last == id) null else s"\"$id\"" // skip default
          optional("override def artifactName = ", name)
        }
        val resourcesSetting =
          resources(
            mavenModel.getBuild.getResources.iterator().asScala
              .map(_.getDirectory)
              .map(os.Path(_))
              .filterNot((millSourcePath / "src/main/resources").equals)
              .map(_.relativeTo(millSourcePath))
          )
        val javacOptionsSetting =
          optional("override def javacOptions = Seq(\"", javacOptions, "\",\"", "\")")
        val depsSettings = compileDeps.settings("ivyDeps", "moduleDeps")
        val compileDepsSettings = providedDeps.settings("compileIvyDeps", "compileModuleDeps")
        val runDepsSettings = runtimeDeps.settings("runIvyDeps", "runModuleDeps")
        val pomPackagingTypeSetting = {
          val packagingType = packaging match {
            case "jar" => null // skip default
            case "pom" => "PackagingType.Pom"
            case pkg => s"\"$pkg\""
          }
          optional(s"override def pomPackagingType = ", packagingType)
        }
        val pomParentProjectSetting = {
          val parent = mavenModel.getParent
          if (null == parent) ""
          else {
            val group = parent.getGroupId
            val id = parent.getArtifactId
            val version = parent.getVersion
            s"override def pomParentProject = Some(Artifact(\"$group\", \"$id\", \"$version\"))"
          }
        }
        val metadataSettings =
          if (config.baseModule.isEmpty) publishMetadata(mavenModel, config) else ""
        val testModuleTypedef = {
          val resources = mavenModel.getBuild.getTestResources.iterator().asScala
            .map(_.getDirectory)
            .map(os.Path(_))
            .filterNot((millSourcePath / "src/test/resources").equals)

          val testFramework = intermediateBuild.testFramework

          if (
            "pom" != packaging && (
              os.exists(millSourcePath / "src/test") || resources.nonEmpty || testFramework.nonEmpty
            )
          ) {
            val supertype = "MavenTests"
            val testMillSourcePath = millSourcePath / "test"
            val resourcesRel = resources.map(_.relativeTo(testMillSourcePath))

            testDeps.testTypeDef(supertype, testFramework, resourcesRel, config)
          } else ""
        }

        s"""$artifactNameSetting
           |
           |$resourcesSetting
           |
           |$javacOptionsSetting
           |
           |$depsSettings
           |
           |$compileDepsSettings
           |
           |$runDepsSettings
           |
           |$pomPackagingTypeSetting
           |
           |$pomParentProjectSetting
           |
           |$metadataSettings
           |
           |$testModuleTypedef""".stripMargin
      }

      val outer = if (dirs.isEmpty) baseModuleTypedef else ""

      build.copy(module = BuildCodeObject(imports, companions, supertypes, inner, outer))
    }
  }

  private type Gav = (String, String, String)
  private object Id {

    def apply(mvn: Dependency): Gav =
      (mvn.getGroupId, mvn.getArtifactId, mvn.getVersion)

    def apply(mvn: Model): Gav =
      (mvn.getGroupId, mvn.getArtifactId, mvn.getVersion)
  }

  // TODO consider moving to Intermediate
  private type Package = String
  private type ModuleDependencies = SortedSet[Package]
  private type IvyInterpolated = String
  private type IvyDependencies = SortedSet[String] // interpolated or named

  private case class ScopeDependenciesInCode(
      ivyDeps: IvyDependencies,
      moduleDeps: ModuleDependencies
  ) {

    def settings(ivyDepsName: String, moduleDepsName: String): String = {
      val ivyDepsSetting =
        optional(s"override def $ivyDepsName = Agg", ivyDeps)
      val moduleDepsSetting =
        optional(s"override def $moduleDepsName = Seq", moduleDeps)

      s"""$ivyDepsSetting
         |
         |$moduleDepsSetting""".stripMargin
    }

    def testTypeDef(
        supertype: String,
        testModule: ScopeDependenciesInCode.TestModule,
        resourcesRel: IterableOnce[os.RelPath],
        config: Config
    ): String =
      if (ivyDeps.isEmpty && moduleDeps.isEmpty) ""
      else {
        val name = backtickWrap(config.testModule)
        val declare = testModule match {
          case Some(module) => s"object $name extends $supertype with $module"
          case None => s"trait $name extends $supertype"
        }
        val resourcesSetting = resources(resourcesRel)
        val moduleDepsSetting =
          optional(s"override def moduleDeps = super.moduleDeps ++ Seq", moduleDeps)
        val ivyDepsSetting = optional(s"override def ivyDeps = super.ivyDeps() ++ Agg", ivyDeps)

        s"""$declare {
           |
           |$resourcesSetting
           |
           |$moduleDepsSetting
           |
           |$ivyDepsSetting
           |}""".stripMargin
      }
  }
  // TODO consider moving some of the functions in the object outside
  private object ScopeDependenciesInCode {
    private type TestModule = Option[String]

    type AllDependenciesInCode = GenericBuildModels.AllDependencies[ScopeDependenciesInCode]

    case class CompanionsAndAllDependencies(
        companions: BuildCodeObject.Companions,
        allDependenciesInCode: AllDependenciesInCode
    )

    /**
     * @param module for output only
     */
    def toIntermediateExternalDependency(dependency: Dependency, module: String) = {
      val groupId = dependency.getGroupId
      val artifactId = dependency.getArtifactId
      val version = dependency.getVersion
      val tpe = dependency.getType match {
        case null | "" | "jar" => None // skip default
        case tpe => Some(tpe)
      }
      val classifier = dependency.getClassifier match {
        case null | "" => None
        case s"$${$v}" => // drop values like ${os.detected.classifier}
          println(
            s"[$module] dropping classifier $${$v} for dependency $groupId:$artifactId:$version"
          )
          None
        case classifier => Some(classifier)
      }
      val exclusions = dependency.getExclusions.iterator.asScala
        .map(exclusion => Exclusion(exclusion.getGroupId, exclusion.getArtifactId)).toSeq
      ExternalDependency(groupId, artifactId, version, tpe, classifier, exclusions)
    }

    object Scopes {
      val COMPILE = "compile"
      val PROVIDED = "provided"
      val RUNTIME = "runtime"
      val TEST = "test"
      val ALL_SET: Set[String] = Set(COMPILE, PROVIDED, RUNTIME, TEST)
    }

    // TODO move out
    def intermediateAllDependencies(
        model: Model,
        packages: PartialFunction[Gav, Package]
    ): IntermediateBuildModels.AllDependencies = {
      // refactored to a functional approach from the original imperative code
      val dependenciesByScope = model.getDependencies.asScala.view
        .flatMap(dependency => {
          val scope = dependency.getScope
          val needed = Scopes.ALL_SET.contains(scope)
          val id = Id(dependency)
          if (needed) Some((scope, id, dependency))
          else {
            println(s"skipping dependency $id with $scope scope")
            None
          }
        })
        .groupBy(_._1)
        .view.mapValues(deps => {
          val (external: SortedSet[ExternalDependency], module: SortedSet[Package]) =
            deps.partitionMap({ case (_, id, dependency) =>
              if (packages.isDefinedAt(id)) Right(packages(id))
              else Left(toIntermediateExternalDependency(dependency, ???))
            })
          ScopeDependencies(external, module)
        }).toMap

      AllDependencies(
        dependenciesByScope(Scopes.COMPILE),
        dependenciesByScope(Scopes.PROVIDED),
        dependenciesByScope(Scopes.RUNTIME),
        dependenciesByScope(Scopes.TEST)
      )
    }

    def toCompanionsAndAllDependenciesInCode(
        allDependencies: IntermediateBuildModels.AllDependencies,
        config: Config
    ): CompanionsAndAllDependencies = {
      // originally named `ivyInterp`
      def toDependencyInCode(dependency: ExternalDependency) = {
        val group = dependency.groupId
        val id = dependency.artifactId
        val version = dependency.version
        val tpe = dependency.`type`.fold("")(tpe => s";type=$tpe")
        val classifier = dependency.classifier.fold("")(classifier => s";classifier=$classifier")
        val exclusions = dependency.exclusions.iterator
          .map(x => s";exclude=${x.groupId}:${x.artifactId}")
          .mkString

        s"ivy\"$group:$id:$version$tpe$classifier$exclusions\""
      }

      config.depsObject.fold(CompanionsAndAllDependencies(
        SortedMap.empty,
        allDependencies.map { case ScopeDependencies(externalDependencies, moduleDependencies) =>
          ScopeDependenciesInCode(externalDependencies.map(toDependencyInCode), moduleDependencies)
        }
      ))(objectName => {
        val objectFieldsAndTheirReferencesOfAllDependencies = allDependencies.map({
          case ScopeDependencies(externalDependencies, moduleDependencies) =>
            (
              {
                externalDependencies.iterator.map(dependency => {
                  val dependencyName =
                    backtickWrap(s"${dependency.groupId}:${dependency.artifactId}")
                  ((dependencyName, toDependencyInCode(dependency)), s"$objectName.$dependencyName")
                })
              }.toSeq,
              moduleDependencies
            )
        })

        val namedIvyDependencies =
          objectFieldsAndTheirReferencesOfAllDependencies.all.iterator.flatMap(_._1).map(_._1)
        val companions = SortedMap((objectName, SortedMap.from(namedIvyDependencies)))

        val allDependenciesInCode = objectFieldsAndTheirReferencesOfAllDependencies.map {
          case (externalDependencies, moduleDependencies) =>
            ScopeDependenciesInCode(
              SortedSet.from(externalDependencies.iterator.map(_._2)),
              moduleDependencies
            )
        }

        CompanionsAndAllDependencies(companions, allDependenciesInCode)
      })
    }

    // TODO delete this old function
    def allDependencies(
        model: Model,
        packages: PartialFunction[Gav, Package],
        config: Config
    ): AllDependenciesInCode = {
      // TODO move this
      val module = model.getProjectDirectory.getName

      ???
    }
  }

  private def publishMetadata(model: Model, config: Config): String = {
    val description = escape(model.getDescription)
    val organization = escape(model.getGroupId)
    val url = escape(model.getUrl)
    val licenses = model.getLicenses.iterator().asScala.map { license =>
      val id = escape(license.getName)
      val name = id
      val url = escape(license.getUrl)
      val isOsiApproved = false
      val isFsfLibre = false
      val distribution = "\"repo\""

      s"License($id, $name, $url, $isOsiApproved, $isFsfLibre, $distribution)"
    }.mkString("Seq(", ", ", ")")
    val versionControl = Option(model.getScm).fold(Seq.empty[String]) { scm =>
      val repo = escapeOption(scm.getUrl)
      val conn = escapeOption(scm.getConnection)
      val devConn = escapeOption(scm.getDeveloperConnection)
      val tag = escapeOption(scm.getTag)

      Seq(repo, conn, devConn, tag)
    }.mkString("VersionControl(", ", ", ")")
    val developers = model.getDevelopers.iterator().asScala.map { dev =>
      val id = escape(dev.getId)
      val name = escape(dev.getName)
      val url = escape(dev.getUrl)
      val org = escapeOption(dev.getOrganization)
      val orgUrl = escapeOption(dev.getOrganizationUrl)

      s"Developer($id, $name, $url, $org, $orgUrl)"
    }.mkString("Seq(", ", ", ")")
    val publishVersion = escape(model.getVersion)
    val publishProperties =
      if (config.publishProperties.value) {
        val props = model.getProperties
        props.stringPropertyNames().iterator().asScala
          .map(key => s"(\"$key\", ${escape(props.getProperty(key))})")
      } else Seq.empty

    val pomSettings =
      s"override def pomSettings = PomSettings($description, $organization, $url, $licenses, $versionControl, $developers)"
    val publishVersionSetting =
      s"override def publishVersion = $publishVersion"
    val publishPropertiesSetting =
      optional(
        "override def publishProperties = super.publishProperties() ++ Map",
        publishProperties
      )

    s"""$pomSettings
       |
       |$publishVersionSetting
       |
       |$publishPropertiesSetting""".stripMargin
  }

  private def escape(value: String): String =
    pprint.Util.literalize(if (value == null) "" else value)

  private def escapeOption(value: String): String =
    if (null == value) "None" else s"Some(${escape(value)})"

  private def optional(start: String, value: String): String =
    if (null == value) ""
    else s"$start$value"

  private def optional(construct: String, args: IterableOnce[String]): String =
    optional(construct + "(", args, ",", ")")

  private def optional(
      start: String,
      vals: IterableOnce[String],
      sep: String,
      end: String
  ): String = {
    val itr = vals.iterator
    if (itr.isEmpty) ""
    else itr.mkString(start, sep, end)
  }

  private def resources(relPaths: IterableOnce[os.RelPath]): String = {
    val itr = relPaths.iterator
    if (itr.isEmpty) ""
    else
      itr
        .map(rel => s"PathRef(millSourcePath / \"$rel\")")
        .mkString(s"override def resources = Task.Sources { super.resources() ++ Seq(", ", ", ") }")
  }
}

@mill.api.internal
trait CommonMavenPomBuildGenConfig extends CommonBuildGenConfig with ModelerConfig {
  // This message is different from the common one.
  @arg(doc = "capture properties defined in pom.xml for publishing")
  override def publishProperties: Flag
  @arg(doc = "use cache for Maven repository system")
  override def cacheRepository: Flag
  @arg(doc = "process Maven plugin executions and configurations")
  override def processPlugins: Flag
}
