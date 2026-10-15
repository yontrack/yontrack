import java.time.LocalDate

plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":ontrack-extension-support"))
    api(project(":ontrack-ui-support"))

    implementation(project(":ontrack-ui-graphql"))

    testImplementation(testFixtures(project(":ontrack-it-utils")))

    testRuntimeOnly(project(":ontrack-service"))
    testRuntimeOnly(project(":ontrack-repository-impl"))
}

// The evaluation key of compose/docker-compose.yml must not expire unnoticed (#2095). Its check reads a
// file of another directory and depends on the day, neither an input of `test`: restored from the build
// cache, it would never fail. It runs in a task of its own, whose inputs are that file and the day, and
// which `test` - and so `./gradlew test` and `build` - runs.
val composeFile = rootProject.file("compose/docker-compose.yml")
val composeEvaluationLicenseKeyTest = tasks.register<Test>("composeEvaluationLicenseKeyTest") {
    group = "verification"
    description = "Checks that the evaluation licence key of compose/docker-compose.yml is not about to expire"
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/ComposeEvaluationLicenseKeyTest.class")
    inputs.file(composeFile).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("composeFile")
    inputs.property("day", LocalDate.now().toString())
    systemProperty("ontrack.test.compose-file", composeFile.absolutePath)
}

tasks.named<Test>("test") {
    exclude("**/ComposeEvaluationLicenseKeyTest.class")
    dependsOn(composeEvaluationLicenseKeyTest)
}
