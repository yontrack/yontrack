import com.avast.gradle.dockercompose.ComposeExtension

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.spring") version "2.2.20"
    id("org.springframework.boot") version "3.5.9" apply false
    id("io.spring.dependency-management") version "1.1.7"
    id("com.avast.gradle.docker-compose") version "0.17.12"
    id("com.google.cloud.tools.jib") version "3.5.1" apply false
    id("com.github.node-gradle.node") version "7.1.0" apply false
    // Versioning logic moved into buildSrc plugin
    id("net.nemerosa.ontrack.versioning")
}

/**
 * Meta information
 */

group = "net.nemerosa.ontrack"

/**
 * Versioning is provided by the net.nemerosa.ontrack.versioning plugin in buildSrc
 */

/**
 * Sharing all Spring Boot dependencies
 */

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {

    apply(plugin = "io.spring.dependency-management")

    version = rootProject.version

    val jjwtVersion = "0.12.6"
    val greenMailVersion = "1.6.15"
    val mockkVersion = "1.13.17"

    dependencyManagement {
        imports {
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        }
        dependencies {
            dependency("commons-io:commons-io:2.18.0")
            dependency("org.jsoup:jsoup:1.19.1")
            dependency("org.apache.commons:commons-math3:3.6.1")
            dependency("org.apache.commons:commons-text:1.13.0")
            dependency("org.jgrapht:jgrapht-core:1.5.2")
            dependency("com.opencsv:opencsv:5.10")
            dependency("com.networknt:json-schema-validator:1.5.5")
            dependency("org.gitlab4j:gitlab4j-api:6.1.0")
            dependency("com.slack.api:slack-api-client:1.38.0")
            dependency("org.springframework.vault:spring-vault-core:3.1.2")

            dependency("io.jsonwebtoken:jjwt-api:$jjwtVersion")
            dependency("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
            dependency("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

            dependency("com.icegreen:greenmail:$greenMailVersion")
            dependency("com.icegreen:greenmail-spring:$greenMailVersion")

            dependency("io.mockk:mockk:${mockkVersion}")
            dependency("io.mockk:mockk-jvm:${mockkVersion}")
            dependency("io.mockk:mockk-dsl:${mockkVersion}")
            dependency("io.mockk:mockk-dsl-jvm:${mockkVersion}")

            // Git repository support TODO Will be removed in V6
            dependency("org.eclipse.jgit:org.eclipse.jgit:6.6.1.202309021850-r")
        }
    }

    // Kotlin
    extra["kotlin.version"] = "2.2.20"
    // Kotlin Coroutines
    extra["kotlin-coroutines.version"] = "1.10.2"

}

// ===================================================================================================================
// Docker compose
// ===================================================================================================================

configure<ComposeExtension> {
    createNested("integrationTest").apply {
        useComposeFiles.addAll(listOf("compose/docker-compose-it.yml"))
        setProjectName("it")
    }
    createNested("local").apply {
        useComposeFiles.addAll(listOf("compose/docker-compose-local.yml"))
        setProjectName("local")
    }
}

// The development stack is driven by scripts/dev-stack.sh rather than by the
// compose plugin: it supervises the backend and the frontend as well as the
// middleware, and it has to keep working when the build itself does not.
// These tasks are thin delegations so that there is a single implementation
// of the slot and port arithmetic.

val devStackScript = "$rootDir/scripts/dev-stack.sh"

tasks.register<Exec>("devStackUp") {
    group = "development"
    description = "Starts the local development stack (middleware, backend, frontend)"
    commandLine(devStackScript, "up")
}

tasks.register<Exec>("devStackDown") {
    group = "development"
    description = "Stops the local development stack"
    commandLine(devStackScript, "down")
}

tasks.named("localComposeUp") {
    dependsOn(":ontrack-ui:jibDockerBuild")
    dependsOn(":ontrack-web-core:dockerBuild")
}

// ===================================================================================================================
// Java projects
// ===================================================================================================================

val javaProjects = subprojects.filter {
    it.path != ":ontrack-web-core"
}

configure(javaProjects) {

    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
            languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        }
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
        exclude("**/*IT.class")
    }

    val integrationTest by tasks.registering(Test::class) {
        group = "verification"
        description = "Integration tests"
        useJUnitPlatform()

        // Only include classes whose names end with 'IT'
        include("**/*IT.class")
        // Set the test classes directory to be the same as the unit tests
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath

        shouldRunAfter("test")
        minHeapSize = "128m"
        maxHeapSize = "3072m"
        dependsOn(":integrationTestComposeUp")
        finalizedBy(":integrationTestComposeDown")
    }

    // Synchronization with shutting down the database
    rootProject.tasks.named("integrationTestComposeDown") {
        mustRunAfter(integrationTest)
    }

    // Inclusion in lifecycle
    tasks.check {
        dependsOn(integrationTest)
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
        implementation("jakarta.validation:jakarta.validation-api")

        runtimeOnly("org.hibernate.validator:hibernate-validator")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.junit.vintage:junit-vintage-engine") {
            exclude(group = "org.hamcrest", module = "hamcrest-core")
        }
        testImplementation("io.mockk:mockk")
        testImplementation("io.mockk:mockk-jvm")
        testImplementation("io.mockk:mockk-dsl")
        testImplementation("io.mockk:mockk-dsl-jvm")

        // See https://github.com/junit-team/junit5/issues/4374
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    }

}
