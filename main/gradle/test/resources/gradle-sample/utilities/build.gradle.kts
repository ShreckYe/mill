plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    api(project(":list"))
    api(project(":utilities:deeply-nested"))

    testImplementation("org.testng:testng:7.10.2")
}

tasks.named<Test>("test") {
    useTestNG()
}
