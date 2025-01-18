package mill.main.gradle.plugin

import org.gradle.api.{Plugin, Project, Task}

import scala.jdk.CollectionConverters.SetHasAsScala

class GenerateBuildMetadataPlugin extends Plugin[Project] {
  override def apply(project: Project): Unit = {
    project.getTasks.register(
      "generateMillInitBuildMetadata",
      { task: Task =>
        // Superconfigurations include `api.
        project.getConfigurations.getByName("implementation").getAllDependencies.asScala
        ???
      }
    )
  }
}
