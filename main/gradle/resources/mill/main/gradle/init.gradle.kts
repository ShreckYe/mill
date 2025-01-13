allprojects {
    apply("maven-publish")

    extensions.configure<PublishingExtension>("publishing") {
        publications {
            create<MavenPublication>("fallbackMavenForMillInit") {
                from(components["java"])
            }
        }
    }
}
