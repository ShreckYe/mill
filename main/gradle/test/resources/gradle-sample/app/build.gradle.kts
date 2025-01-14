plugins {
    id("buildlogic.java-application-conventions")
}

dependencies {
    implementation("org.apache.commons:commons-text")
    implementation(project(":utilities"))
    compileOnly("org.apache.commons:commons-lang3:3.17.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass = "org.example.app.App"
}

tasks.withType<JavaCompile> {
    options.compilerArgs = listOf("-Xlint:unchecked")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
