import com.avast.gradle.dockercompose.ComposeExtension
import net.nemerosa.ontrack.build.DependencyLocking
import net.nemerosa.ontrack.build.ItStack
import org.springframework.boot.gradle.plugin.SpringBootPlugin

// Locks the plugin classpath of the root project into buildscript-gradle.lockfile (#1752). It has
// to be done here, before the plugins below are resolved; the subprojects' plugin classpaths are
// locked by DependencyLocking.
buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
    dependencyLocking {
        lockMode.set(LockMode.STRICT)
    }
}

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.spring") version "2.2.20"
    id("org.springframework.boot") version "4.1.1" apply false
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
 * Sharing all Spring Boot dependencies: see the platforms declared on every Java project below.
 */

allprojects {
    repositories {
        mavenCentral()
    }

    // STRICT dependency locking of every configuration, and the resolveAndLockAll task (#1752).
    // Lockfiles are written with `./gradlew resolveAndLockAll --write-locks`, see DEVELOPMENT.md.
    DependencyLocking.configure(this)
}

subprojects {

    version = rootProject.version

}

// ===================================================================================================================
// Docker compose
// ===================================================================================================================

// The integration test stack is an *instance* of this checkout, the way the
// development stack is: its Compose project and every port it publishes are
// derived from a slot, so that several worktrees can run `integrationTest` at
// the same time. The main working copy takes slot 0 and keeps the historical
// ports -- which is what every CI runner, a fresh clone, also gets.
// See docs/adr/0012-parallel-integration-test-stacks.md.
val itStack = ItStack.resolve(rootDir)

configure<ComposeExtension> {
    createNested("integrationTest").apply {
        useComposeFiles.addAll(listOf("compose/docker-compose-it.yml"))
        setProjectName(itStack.projectName)
        environment.putAll(itStack.composeEnvironment)
    }
    createNested("local").apply {
        useComposeFiles.addAll(listOf("compose/docker-compose-local.yml"))
        setProjectName("local")
    }
}

tasks.named("integrationTestComposeUp") {
    doFirst {
        // Recorded before the stack comes up rather than after, so that the
        // ports are discoverable even when it fails to start.
        itStack.writeInstanceEnv(rootProject.file(ItStack.INSTANCE_ENV_PATH))
        logger.lifecycle("[it-stack] ${itStack.describe()}")
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

        // Point the tests at this checkout's instance of the stack. Without
        // these the defaults baked into the code -- localhost:5432 and
        // friends -- would send every worktree to the same containers.
        itStack.systemProperties.forEach { (key, value) -> systemProperty(key, value) }
    }

    // Synchronization with shutting down the database
    rootProject.tasks.named("integrationTestComposeDown") {
        mustRunAfter(integrationTest)
    }

    // Inclusion in lifecycle
    tasks.check {
        dependsOn(integrationTest)
    }

    // `org.hamcrest:hamcrest-core:3.0` is an empty deprecation stub -- a single class named
    // HamcrestCoreIsDeprecated -- standing in for `org.hamcrest:hamcrest`, which carries the real
    // org.hamcrest.core classes and is on the test classpaths anyway. It arrives through junit:junit 4,
    // and the build has always excluded it, but only on the junit-vintage-engine edge; junit:junit also
    // comes in through ontrack-test-utils, and under io.spring.dependency-management the module stayed
    // on every classpath regardless. With the BOM as a platform it lands on the runtime test classpaths
    // but not on the compile ones, and that is not a state dependency locking can hold:
    // `resolveAndLockAll` resolves with the lock constraints off and records no hamcrest-core on
    // testCompileClasspath, while the compile tasks resolve with them on and find one -- "Resolved
    // 'org.hamcrest:hamcrest-core:3.0' which is not part of the dependency lock state". Excluding it from
    // every configuration is what the build meant in the first place, and it is absent either way. #1753
    configurations.configureEach {
        exclude(group = "org.hamcrest", module = "hamcrest-core")
    }

    // ===============================================================================================================
    // Dependency management (#1753)
    //
    // The Spring Boot BOM used to be imported by io.spring.dependency-management, applied to every
    // subproject; it is now a Gradle platform. The two are not the same mechanism: the plugin
    // *overrode* the version of every module it managed, wherever it appeared in the graph, while a
    // platform only takes part in conflict resolution. Every difference that makes shows up in the
    // lockfiles introduced by #1752, which is why the switch was done after locking rather than with
    // it.
    //
    // `platform`, not `enforcedPlatform`: this build compiles with a Kotlin far newer than the one
    // the BOM pins, and an enforced platform would force org.jetbrains.kotlin:* back down to the
    // BOM's 1.9.25 with nothing able to lift it again.
    //
    // The BOM property overrides that went with the plugin -- `extra["kotlin.version"]` and
    // `extra["kotlin-coroutines.version"]`, which io.spring.dependency-management resolved against
    // the project's extra properties -- have no equivalent for a Gradle platform: the properties of
    // a published BOM are already substituted in its POM. They are replaced by declaring, at the
    // versions this build wants, the two BOMs that the Spring Boot BOM imports itself; conflict
    // resolution then keeps the higher of the two.
    //
    // The `dependencyManagement { dependencies { ... } }` entries become plain constraints. None of
    // those modules is managed by the Spring Boot BOM, so they only ever supplied a version to a
    // module declared without one.
    // ===============================================================================================================

    val kotlinVersion = "2.2.20"
    val kotlinCoroutinesVersion = "1.10.2"
    val jjwtVersion = "0.12.6"
    val greenMailVersion = "1.6.15"
    val mockkVersion = "1.13.17"
    val jgitVersion = "6.6.1.202309021850-r"

    // The BOMs. The Spring Boot one is what io.spring.dependency-management imported; the other two
    // are the BOMs it imports itself, restated at the versions this build wants, in place of the
    // `extra["kotlin.version"]` / `extra["kotlin-coroutines.version"]` property overrides.
    val platforms = listOf(
        SpringBootPlugin.BOM_COORDINATES,
        "org.jetbrains.kotlin:kotlin-bom:$kotlinVersion",
        "org.jetbrains.kotlinx:kotlinx-coroutines-bom:$kotlinCoroutinesVersion",
    )

    // The former `dependencyManagement { dependencies { ... } }` entries, one constraint each.
    val versionConstraints = listOf(
        "commons-io:commons-io:2.18.0",
        "org.jsoup:jsoup:1.19.1",
        "org.apache.commons:commons-math3:3.6.1",
        "org.apache.commons:commons-text:1.13.0",
        "org.jgrapht:jgrapht-core:1.5.2",
        "com.opencsv:opencsv:5.10",
        "com.networknt:json-schema-validator:1.5.5",
        "org.gitlab4j:gitlab4j-api:6.1.0",
        "com.slack.api:slack-api-client:1.38.0",
        "org.springframework.vault:spring-vault-core:3.1.2",

        "io.jsonwebtoken:jjwt-api:$jjwtVersion",
        "io.jsonwebtoken:jjwt-impl:$jjwtVersion",
        "io.jsonwebtoken:jjwt-jackson:$jjwtVersion",

        "com.icegreen:greenmail:$greenMailVersion",
        "com.icegreen:greenmail-spring:$greenMailVersion",

        "io.mockk:mockk:$mockkVersion",
        "io.mockk:mockk-jvm:$mockkVersion",
        "io.mockk:mockk-dsl:$mockkVersion",
        "io.mockk:mockk-dsl-jvm:$mockkVersion",

        // Git repository support TODO Will be removed in V6
        "org.eclipse.jgit:org.eclipse.jgit:$jgitVersion",
    )

    // Declared on every dependency bucket of every source set -- main, test, and the testFixtures
    // one that java-test-fixtures adds in a dozen projects -- because a resolvable classpath only
    // ever extends the buckets of its own source set, and the Kotlin plugin's `*DependenciesMetadata`
    // configurations extend `api` and `compileOnly` rather than `implementation`. The plugin this
    // replaces covered every configuration of the project, so this is the closest equivalent.
    //
    // `configurations.matching` rather than a direct lookup: `api` and `compileOnlyApi` only exist
    // once java-library is applied, which happens when the subproject's own build script is
    // evaluated -- after this one.
    sourceSets.configureEach {
        val buckets = setOf(
            apiConfigurationName,
            compileOnlyApiConfigurationName,
            implementationConfigurationName,
            compileOnlyConfigurationName,
            runtimeOnlyConfigurationName,
            annotationProcessorConfigurationName,
        )
        configurations.matching { it.name in buckets }.configureEach {
            platforms.forEach { dependencies.add(project.dependencies.platform(it)) }
            versionConstraints.forEach {
                dependencyConstraints.add(project.dependencies.constraints.create(it))
            }
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
        implementation("jakarta.validation:jakarta.validation-api")

        runtimeOnly("org.hibernate.validator:hibernate-validator")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.junit.vintage:junit-vintage-engine")
        testImplementation("io.mockk:mockk")
        testImplementation("io.mockk:mockk-jvm")
        testImplementation("io.mockk:mockk-dsl")
        testImplementation("io.mockk:mockk-dsl-jvm")

        // See https://github.com/junit-team/junit5/issues/4374
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    }

}
