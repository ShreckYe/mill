package mill.main.gradle;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.util.internal.VersionNumber;

/**
 * A model containing the <a href="https://docs.gradle.org/current/userguide/java_plugin.html">Gradle Java Plugin<a/> settings for a project.
 */
public interface JavaModel extends Serializable {

  List<String> javacOptions();

  List<Config> configs();

  class Impl implements JavaModel {

    private final List<String> javacOptions;
    private final List<Config> configs;

    public Impl(List<String> javacOptions, List<Config> configs) {
      this.javacOptions = javacOptions;
      this.configs = configs;
    }

    @Override
    public List<String> javacOptions() {
      return javacOptions;
    }

    @Override
    public List<Config> configs() {
      return configs;
    }
  }

  static JavaModel from(Project project) {
    if (project.getPluginManager().hasPlugin("java")) {
      List<String> javacOptions = project
          .getTasks()
          .named(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaCompile.class)
          .map(task -> task.getOptions().getAllCompilerArgs())
          .getOrElse(Collections.emptyList());
      VersionNumber gradleVersionNumber =
          VersionNumber.parse(project.getGradle().getGradleVersion());
      List<Config> configs = project.getConfigurations().stream()
          .map(conf -> Config.from(conf, gradleVersionNumber))
          .collect(Collectors.toList());
      return new Impl(javacOptions, configs);
    }
    return null;
  }

  interface Config extends Serializable {

    String name();

    List<ProjectDep> projectDeps();
    List<ExternalDep> externalDeps();

    class Impl implements Config {

      private final String config;
      private final List<ProjectDep> projectDeps;
      private final List<ExternalDep> externalDeps;

      public Impl(String config, List<ProjectDep> projectDeps, List<ExternalDep> externalDeps) {
        this.config = config;
        this.projectDeps = projectDeps;
        this.externalDeps = externalDeps;
      }

      @Override
      public String name() {
        return config;
      }

      @Override
      public List<ProjectDep> projectDeps() {
        return projectDeps;
      }
      @Override
      public List<ExternalDep> externalDeps(){
          return externalDeps;
      }
    }

      static Config from(Configuration conf, VersionNumber gradleVersionNumber) {
          String name = conf.getName();

          Map<Boolean, List<Dependency>> partitioned = conf.getDependencies().stream()
              .collect(Collectors.partitioningBy(dep -> {
                  if (dep instanceof ProjectDependency)
                      return false;
                  else if (dep instanceof ExternalDependency) return true;
                  else throw new IllegalArgumentException("unsupported dependency type: " + dep.getClass());
              }))

          return new Impl(name,
              partitioned.get(false).stream().map(dep -> ProjectDep.from((ProjectDependency) dep, gradleVersionNumber)).toList(),
              partitioned.get(true).stream().map(dep -> ExternalDep.from((ExternalDependency) dep)).toList());
      }
  }

  /*
  Serializing `ProjectDep`s and `ExternalDep`s as subtypes of `Dep` in a shared `List<Dep>` doesn't work well with serialization.
  `instanceOf`/`isInstanceOf` checks on the deserialized objects don't work as expected.
  */
    
  interface ProjectDep /*extends Dep*/ {
    String path();

    class Impl implements ProjectDep {
      private final String path;

      public Impl(String path) {
        this.path = path;
      }

      @Override
      public String path() {
        return path;
      }
    }

    static ProjectDep from(ProjectDependency dep, VersionNumber gradleVersionNumber) {
      //noinspection deprecation
      return new Impl(
          gradleVersionNumber.compareTo(VersionNumber.parse("8.11")) >= 0
              ? dep.getPath()
              : dep.getDependencyProject().getPath());
    }
  }

  interface ExternalDep /*extends Dep*/ {
    String group();

    String name();

    String version();

    class Impl implements ExternalDep {
      private final String group;
      private final String name;
      private final String version;

      public Impl(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
      }

      @Override
      public String group() {
        return group;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public String version() {
        return version;
      }
    }

    static ExternalDep from(ExternalDependency dep) {
      return new Impl(dep.getGroup(), dep.getName(), dep.getVersion());
    }
  }
}
