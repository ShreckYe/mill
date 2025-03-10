package mill.main.gradle;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
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

    List<Dep> deps();

    class Impl implements Config {

      private final String config;
      private final List<Dep> deps;

      public Impl(String config, List<Dep> deps) {
        this.config = config;
        this.deps = deps;
      }

      @Override
      public String name() {
        return config;
      }

      @Override
      public List<Dep> deps() {
        return deps;
      }
    }

    static Config from(Configuration conf, VersionNumber gradleVersionNumber) {
      String name = conf.getName();
      List<Dep> deps = conf.getDependencies().stream()
          .map(dep -> Dep.from(dep, gradleVersionNumber))
          .collect(Collectors.toList());
      return new Impl(name, deps);
    }
  }

  interface Dep extends Serializable {
    static Dep from(Dependency dep, VersionNumber gradleVersionNumber) {
      if (dep instanceof ProjectDependency)
        return ProjectDep.from((ProjectDependency) dep, gradleVersionNumber);
      else if (dep instanceof ExternalDependency) return ExternalDep.from((ExternalDependency) dep);
      else throw new IllegalArgumentException("unsupported dependency type: " + dep.getClass());
    }
  }

  interface ProjectDep extends Dep {
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

  interface ExternalDep extends Dep {
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
