plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(project(":ontrack-json"))
    api(project(":ontrack-common"))
    api("org.springframework:spring-tx")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(project(":ontrack-job"))
    implementation("org.apache.commons:commons-text")
    implementation("org.springframework:spring-context")
    implementation("org.springframework.security:spring-security-core")
    implementation("org.slf4j:slf4j-api")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation(project(":ontrack-test-utils"))

    testFixturesImplementation("org.springframework:spring-context")
}

// The deprecation marker check (ADR 0018) reads the sources of the whole repository, which are no
// inputs of this module's `test` task: restored from the build cache, it would miss a deprecation
// added to any other module. It runs in a task of its own, whose inputs are those sources, and
// which `test` - and so `./gradlew test` and `build` - runs.
val deprecationMarkersTest = tasks.register<Test>("deprecationMarkersTest") {
    group = "verification"
    description = "Checks the deprecation markers of the whole repository against ADR 0018"
    useJUnitPlatform()
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/DeprecationMarkersRepositoryTest.class")
    inputs.files(
        fileTree(rootDir) {
            include("**/*.kt", "**/*.java", "**/*.graphqls")
            include("ontrack-web-core/**/*.js", "ontrack-web-core/**/*.jsx", "ontrack-web-core/**/*.ts")
            include("ontrack-web-core/**/*.tsx", "ontrack-web-core/**/*.mjs")
            include("ontrack-docs/docs/content/appendix/migration-to-v6.md")
            exclude("**/build/**", "**/node_modules/**", "**/.*/**", "**/src/test/resources/**")
            exclude("ontrack-web-core/coverage/**", "ontrack-web-core/out/**")
        }
    ).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("repositorySources")
}

tasks.named<Test>("test") {
    exclude("**/DeprecationMarkersRepositoryTest.class")
    dependsOn(deprecationMarkersTest)
}
