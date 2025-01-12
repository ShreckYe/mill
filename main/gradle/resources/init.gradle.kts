allprojctes {
    plugins {
        `maven-publish`
    }

    publishing {
        publications {
            create<MavenPublication>("fallbackMavenForMillInit") {
                from(components["java"])
            }
        }
    }
}
