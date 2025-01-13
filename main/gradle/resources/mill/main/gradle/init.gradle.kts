allprojects {
    apply(plugin = "maven-publish")

    group = "fallback.group.for.mill.init"

    afterEvaluate {
        extensions.configure<PublishingExtension>("publishing") {
            publications {
                create<MavenPublication>("fallbackMavenForMillInit") {
                    from(components.findByName("java"))
                }
            }
        }
    }
}
