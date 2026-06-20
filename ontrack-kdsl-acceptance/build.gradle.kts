import com.avast.gradle.dockercompose.ComposeExtension

plugins {
    `java-library`
    id("com.avast.gradle.docker-compose")
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation(project(":ontrack-json"))
    testImplementation(project(":ontrack-common"))
    testImplementation("org.springframework.boot:spring-boot-starter")
    testImplementation("org.springframework:spring-web")
    testImplementation(project(":ontrack-kdsl"))
    testImplementation("com.apollographql.apollo:apollo-api:4.1.1")
    testImplementation("commons-io:commons-io")
    testImplementation("commons-codec:commons-codec")

    testImplementation("org.influxdb:influxdb-java")
    testImplementation(testFixtures(project(":ontrack-extension-github")))
}

// Pre-acceptance tests: starting the environment

configure<ComposeExtension> {
    createNested("kdslAcceptanceTest").apply {
        useComposeFiles.addAll(listOf("${rootDir}/compose/docker-compose-kdsl.yml"))
        setProjectName("kdsl")
        // environment.put("ONTRACK_VERSION", project.version.toString())
        captureContainersOutput.set(true)
        captureContainersOutputToFiles.set(file("build/logs/kdsl/containers"))
        composeLogToFile.set(file("build/logs/kdsl/compose"))
        retainContainersOnStartupFailure.set(true)
    }
    createNested("kdslLdap").apply {
        useComposeFiles.addAll(listOf("${rootDir}/compose/docker-compose-kdsl-ldap.yml"))
        setProjectName("kdsl-ldap")
        // environment.put("ONTRACK_VERSION", project.version.toString())
        captureContainersOutput.set(true)
        captureContainersOutputToFiles.set(file("build/logs/kdsl-ldap/containers"))
        composeLogToFile.set(file("build/logs/kdsl-ldap/compose"))
        retainContainersOnStartupFailure.set(true)
    }
    createNested("kdslOidc").apply {
        useComposeFiles.addAll(listOf("${rootDir}/compose/docker-compose-kdsl-oidc.yml"))
        setProjectName("kdsl-oidc")
        // environment.put("ONTRACK_VERSION", project.version.toString())
        captureContainersOutput.set(true)
        captureContainersOutputToFiles.set(file("build/logs/kdsl-oidc/containers"))
        composeLogToFile.set(file("build/logs/kdsl-oidc/compose"))
        retainContainersOnStartupFailure.set(true)
    }
}

val isCI = System.getenv("CI") == "true"

val kdslAcceptanceTestComposeUp by tasks.named("kdslAcceptanceTestComposeUp") {
    if (!isCI) {
        dependsOn(":ontrack-ui:dockerBuild")
        dependsOn(":ontrack-web-core:dockerBuild")
    }
}

// Post-acceptance tests: stopping the environment

val kdslAcceptanceTestComposeDown by tasks.named("kdslAcceptanceTestComposeDown")

tasks.named("kdslLdapComposeUp") {
    dependsOn(kdslAcceptanceTestComposeDown)
}

tasks.named("kdslOidcComposeUp") {
    dependsOn("kdslLdapComposeDown")
}

// Restricting unit tests

tasks.named<Test>("test") {
    useJUnitPlatform()
    exclude("**/ACC*")
}

// Running the acceptance tests

val kdslAcceptanceTest by tasks.registering(Test::class) {
    useJUnitPlatform()
    mustRunAfter("test")
    include("**/ACC*.class")
    val testFilter = System.getProperty("test.filter")
    if (testFilter != null) {
        filter {
            includeTestsMatching(testFilter)
        }
    }
    minHeapSize = "512m"
    maxHeapSize = "3072m"
    dependsOn(kdslAcceptanceTestComposeUp)
    if (!isCI) {
        finalizedBy(kdslAcceptanceTestComposeDown)
    }
}
