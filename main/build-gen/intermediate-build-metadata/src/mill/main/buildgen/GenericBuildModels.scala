package mill.main.buildgen

@mill.api.internal
object GenericBuildModels {

  /**
   * The parameter names follow Maven's conventions.
   */
  case class AllDependencies[ScopeDependencies](
      compile: ScopeDependencies,
      provided: ScopeDependencies,
      runtime: ScopeDependencies,
      test: ScopeDependencies
      // optional: IntermediateScopeDependencies
  ) {
    def all: Seq[ScopeDependencies] = Seq(compile, provided, runtime, test)
    def map[OutScopeDependencies](f: ScopeDependencies => OutScopeDependencies)
        : AllDependencies[OutScopeDependencies] =
      AllDependencies(f(compile), f(provided), f(runtime), f(test))
  }
}
