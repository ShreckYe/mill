plugins {
    `java-gradle-plugin`
    scala
}

group = "com.lihaoyi"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.scala.library)
    implementation(libs.upickle)
    implementation("org.scala-lang", "scala-library", "2.13.15")
}

gradlePlugin {
    plugins.create("generate-build-metadata-for-mill-init") {
        id = "com.lihaoyi.generate-build-metadata-for-mill-init"
        implementationClass = "mill.main.gradle.plugin.GenerateBuildMetadataPlugin"
    }
}
