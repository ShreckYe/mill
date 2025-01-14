allprojects {
    apply(plugin = "maven-publish")

    group = "dummy.group.from.mill.init.from.gradle"

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
