plugins {
    `java-library`
}

description = "JSON utilities."

dependencies {
    api("tools.jackson.core:jackson-databind")

    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml")
    implementation("org.apache.commons:commons-lang3")
}

/**
 * `NoJackson2GuardTest` reads the sources of the whole repository: it runs in a task of its own,
 * declaring them as its inputs, so that it is re-run, rather than restored from the build cache,
 * when a source of another module changes.
 */
val noJackson2GuardTest = tasks.register<Test>("noJackson2GuardTest") {
    group = "verification"
    description = "Checks that no source of the repository refers to Jackson 2 (ADR 0016)"
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/NoJackson2GuardTest.class")
    inputs.files(
        fileTree(rootDir) {
            include("**/*.kt", "**/*.java")
            exclude("**/build/**", "**/node_modules/**", "**/.*/**")
            // The outputs of :ontrack-docs:buildDocs - Gradle refuses an input tree overlapping them
            exclude("ontrack-docs/site/**", "ontrack-docs/.venv/**")
        }
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("repositorySources")
}

tasks.named<Test>("test") {
    exclude("**/NoJackson2GuardTest.class")
    dependsOn(noJackson2GuardTest)
}
