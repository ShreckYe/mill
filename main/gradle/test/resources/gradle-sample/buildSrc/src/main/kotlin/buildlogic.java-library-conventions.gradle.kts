plugins {
    id("buildlogic.java-common-conventions")
    `java-library`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components.findByName("java"))

            pom {
                name.set("Gradle sample for Mill init")
                description.set("This is a Gradle sample for testing Mill's init command.")

                val urlVal = "https://github.com/com-lihaoyi/mill"
                url.set(urlVal)

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("johnd")
                        name.set("John Doe")
                        email.set("john.doe@example.com")
                    }
                }
                scm {
                    val connectionVal = "scm:git:$urlVal.git"
                    connection.set(connectionVal)
                    developerConnection.set(connectionVal)
                    url.set(urlVal)
                }
            }
        }

        create<MavenPublication>("secondMaven") {}
    }
}
