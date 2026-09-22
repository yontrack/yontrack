import com.avast.gradle.dockercompose.ComposeExtension
import net.nemerosa.ontrack.build.Coverage
import net.nemerosa.ontrack.build.DependencyLocking
import net.nemerosa.ontrack.build.ItStack
import net.nemerosa.ontrack.build.ItStackInstance
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
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
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
//
// As in ontrack-kdsl-acceptance, claiming the slot probes ports and fails when
// none is free, so it happens at *execution* time rather than here: this is the
// root build file, configured by every Gradle invocation, and no invocation
// should pay for -- or die on -- a probe for a stack it is not going to start.
// Naming the Compose project costs nothing and stays eager.
val itNames = ItStack.names(rootDir)
val itStack: ItStackInstance by lazy { ItStack.resolve(rootDir) }
val itComposeEnvironment: Provider<Map<String, String>> = provider { itStack.composeEnvironment }

configure<ComposeExtension> {
    createNested("integrationTest").apply {
        useComposeFiles.addAll(listOf("compose/docker-compose-it.yml"))
        setProjectName(itNames.projectName)
        environment.putAll(itComposeEnvironment)
    }
    createNested("local").apply {
        useComposeFiles.addAll(listOf("compose/docker-compose-local.yml"))
        setProjectName("local")
    }
}

// The one place the slot is claimed on purpose, rather than as a side effect of
// a Compose task reading its ports. The Compose tasks run it first, so a
// checkout with no free slot fails here, with the message as it is written --
// rather than inside Gradle's provider machinery, which buries it under three
// layers of "Failed to query the value of property 'environment'".
val itStackSlot = tasks.register("itStackSlot") {
    group = "verification"
    description = "Claims this checkout's integration test slot and records it in ${ItStack.INSTANCE_ENV_PATH}"
    doFirst {
        // Recorded before the stack comes up rather than after, so that the
        // ports are discoverable even when it fails to start.
        itStack.writeInstanceEnv(rootProject.file(ItStack.INSTANCE_ENV_PATH))
        logger.lifecycle("[it-stack] ${itStack.describe()}")
    }
}

// Compose builds before it starts, and both phases read the ports, so the claim
// has to come before the earliest of them.
listOf("integrationTestComposeBuild", "integrationTestComposeUp").forEach { name ->
    tasks.named(name) {
        dependsOn(itStackSlot)
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

    // ===============================================================================================================
    // Test coverage collection (#1818)
    //
    // The plugin is applied *unconditionally* and only the agent is gated on `-Pcoverage`. That
    // split is not the obvious reading of "behind a property", and it is deliberate: under the
    // STRICT dependency locking of #1752 a configuration with no lock state fails the build, and
    // `resolveAndLockAll --write-locks` records whatever configurations exist when it runs.
    // Applying the plugin conditionally would make `jacocoAgent` and `jacocoAnt` appear and
    // disappear with the property, so the lockfiles could only ever be right for one of the two
    // invocations. Applied always, gated on the agent, there is one lock state for every build.
    //
    // No report task is wired here and nothing hangs off `check`: the reports, their exclusions
    // and the COVERAGE.* stamps belong to #1821 and #1822.
    // ===============================================================================================================

    apply(plugin = "jacoco")

    configure<JacocoPluginExtension> {
        toolVersion = Coverage.JACOCO_VERSION
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
    }

    kotlin {
        jvmToolchain(25)
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
            languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
        }
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
        exclude("**/*IT.class")
        // A module whose tests are all *IT classes still has test classes left after the
        // exclusion -- fixtures, support code -- and Gradle 9 fails a test task which has
        // sources but discovers no test in them. The exclusion is what empties it, on purpose.
        failOnNoDiscoveredTests = false
    }

    val integrationTest = tasks.register<Test>("integrationTest") {
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
        // Contributed as an argument provider rather than as plain system
        // properties so that the slot is claimed when the task runs rather
        // than when the root build file is configured.
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            itStack.systemProperties.map { (key, value) -> "-D$key=$value" }
        })
    }

    // Synchronization with shutting down the database
    rootProject.tasks.named("integrationTestComposeDown") {
        mustRunAfter(integrationTest)
    }

    // The agent, on the test tasks whose Gradle JVM is measured -- `test` and `integrationTest`,
    // per Coverage.SESSION_TYPES. Off unless `-Pcoverage` is set: instrumentation costs run time on
    // every build, and the design accepts that cost on coverage runs only.
    //
    // Each task writes its own `build/jacoco/<task>.exec`, tagged with a session ID naming its
    // origin (`unit`, `integration`, or `integration-<shard>` when COVERAGE_SESSION_SUFFIX is set
    // by the CI workflow). The merged report's Sessions page reads those back (#1821).
    val coverageEnabled = providers.gradleProperty(Coverage.GRADLE_PROPERTY).isPresent
    val coverageSessionSuffix = providers.environmentVariable(Coverage.SESSION_SUFFIX_ENV).orNull

    tasks.withType<Test>().configureEach {
        val coverageSessionId = Coverage.sessionId(name, coverageSessionSuffix)
        extensions.configure<JacocoTaskExtension> {
            isEnabled = coverageEnabled && coverageSessionId != null
            if (coverageSessionId != null) {
                setDestinationFile(
                    layout.buildDirectory.file(Coverage.execFilePath(name)).get().asFile
                )
                sessionId = coverageSessionId
            }
        }
    }

    // Inclusion in lifecycle
    tasks.check {
        dependsOn(integrationTest)
    }

    // `org.hamcrest:hamcrest-core:3.0` is an empty deprecation stub -- a single class named
    // HamcrestCoreIsDeprecated -- standing in for `org.hamcrest:hamcrest`, which carries the real
    // org.hamcrest.core classes and is on the test classpaths anyway. It used to arrive through junit:junit 4,
    // and the build excluded it only on the junit-vintage-engine edge while junit:junit also came in
    // through ontrack-test-utils. With the BOM as a platform it landed on the runtime test classpaths
    // but not on the compile ones, and that is not a state dependency locking can hold:
    // `resolveAndLockAll` resolves with the lock constraints off and records no hamcrest-core on
    // testCompileClasspath, while the compile tasks resolve with them on and find one -- "Resolved
    // 'org.hamcrest:hamcrest-core:3.0' which is not part of the dependency lock state". #1753
    // junit:junit and the vintage engine are gone since #1844; the exclusion stays so that no other path
    // can bring the stub back into that state.
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
    // `platform`, not `enforcedPlatform`: an enforced platform would force org.jetbrains.kotlin:*
    // to the Kotlin the BOM pins with nothing able to lift it again, and this build has compiled
    // with a newer Kotlin than the BOM's before (Boot 3.5 pinned 1.9.25). Boot 4.1.1 pins 2.3.21,
    // the version below, so for now the two agree.
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

    val kotlinVersion = "2.3.21"
    val kotlinCoroutinesVersion = "1.10.2"
    val jjwtVersion = "0.12.6"
    val greenMailVersion = "1.6.15"
    val mockkVersion = "1.14.11"
    val jgitVersion = "6.6.1.202309021850-r"
    val amqpClientVersion = "5.36.0"
    val msgpackCoreVersion = "0.9.12"
    val commonsBeanutilsVersion = "1.11.0"
    val tomcatVersion = "11.0.26"

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
        // Jackson 3 from 3.0 on (#1843); 3.0.6 is the last one on Jackson 3.1, as the Spring Boot BOM
        "com.networknt:json-schema-validator:3.0.6",
        // Jackson 3 providers from 3.0 on; the Spring Boot BOM manages 2.10 (#1843)
        "com.jayway.jsonpath:json-path:3.0.0",
        "com.slack.api:slack-api-client:1.38.0",
        "org.springframework.vault:spring-vault-core:4.1.0",

        "io.jsonwebtoken:jjwt-api:$jjwtVersion",
        "io.jsonwebtoken:jjwt-impl:$jjwtVersion",

        "com.icegreen:greenmail:$greenMailVersion",
        "com.icegreen:greenmail-spring:$greenMailVersion",

        "io.mockk:mockk:$mockkVersion",
        "io.mockk:mockk-jvm:$mockkVersion",
        "io.mockk:mockk-dsl:$mockkVersion",
        "io.mockk:mockk-dsl-jvm:$mockkVersion",

        // Git repository support TODO Will be removed in V6
        "org.eclipse.jgit:org.eclipse.jgit:$jgitVersion",

        // Transitive libraries pinned past their managed versions to clear the HIGHs of the backend
        // image scan. Each pin goes once its source brings a fixed version by itself.
        // - amqp-client (Boot BOM manages 5.30.0): CVE-2026-63337, CVE-2026-69219, CVE-2026-69220,
        //   CVE-2026-75516 (fixed in 5.34.0). Remove once the Spring Boot BOM manages >= 5.34.0.
        "com.rabbitmq:amqp-client:$amqpClientVersion",
        // - msgpack-core (0.9.8 via influxdb-java 2.25): CVE-2026-21452 (fixed in 0.9.11).
        //   Remove once influxdb-java brings msgpack-core >= 0.9.11.
        "org.msgpack:msgpack-core:$msgpackCoreVersion",
        // - commons-beanutils (1.10.0 via opencsv 5.10): CVE-2025-48734 (fixed in 1.11.0).
        //   Remove once opencsv brings commons-beanutils >= 1.11.0.
        "commons-beanutils:commons-beanutils:$commonsBeanutilsVersion",
        // - tomcat-embed-* (Boot BOM 4.1.1 manages 11.0.24): CVE-2026-65182, CVE-2026-65905,
        //   CVE-2026-68525, all CRITICAL (fixed in 11.0.25). All three modules, to keep Tomcat aligned.
        //   Remove once the Spring Boot BOM manages >= 11.0.25.
        "org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion",
        "org.apache.tomcat.embed:tomcat-embed-el:$tomcatVersion",
        "org.apache.tomcat.embed:tomcat-embed-websocket:$tomcatVersion",
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
    //
    // Not on `testApi` / `testCompileOnlyApi`, though: nothing declares a dependency there, and
    // Kotlin 2.3 warns about any dependency in the API buckets of the test source set.
    sourceSets.configureEach {
        val apiBuckets = if (name == SourceSet.TEST_SOURCE_SET_NAME) {
            emptySet()
        } else {
            setOf(apiConfigurationName, compileOnlyApiConfigurationName)
        }
        val buckets = apiBuckets + setOf(
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

    // No Jackson 2 (#1843, ADR 0016). The Elasticsearch client is the last library to bring it, for
    // its `JacksonJsonpMapper` only: Yontrack gives the client the `Jackson3JsonpMapper`. The rule
    // removes it wherever the client comes from -- Spring Data Elasticsearch brings it too.
    dependencies.components.withModule("co.elastic.clients:elasticsearch-java") {
        allVariants {
            withDependencies {
                removeAll {
                    it.group == "com.fasterxml.jackson.core" && it.name in setOf("jackson-core", "jackson-databind")
                }
            }
        }
    }

    dependencies {
        implementation(kotlin("stdlib"))
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
        implementation("jakarta.validation:jakarta.validation-api")

        runtimeOnly("org.hibernate.validator:hibernate-validator")
        // The Validator bean, auto-configured by spring-boot-autoconfigure before Spring Boot 4
        runtimeOnly("org.springframework.boot:spring-boot-validation")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("io.mockk:mockk")
        testImplementation("io.mockk:mockk-jvm")
        testImplementation("io.mockk:mockk-dsl")
        testImplementation("io.mockk:mockk-dsl-jvm")

        // See https://github.com/junit-team/junit5/issues/4374
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    }

}

// ===================================================================================================================
// The local merge-and-report path (#1823)
//
// `./gradlew coverageReport` gives a contributor the reports and the figures the `coverage` job of
// .github/workflows/ci.yml produces, from the execution data this checkout already holds:
//
//     ./gradlew test integrationTest -Pcoverage      # and kdslAcceptanceTest / uiTest, if you have the time
//     ./gradlew coverageReport
//
// It reimplements nothing. The exclusion list, the `jacococli report` invocations and the six sets
// of figures are `scripts/coverage-report.sh` and `scripts/coverage-metrics.sh`, which the
// workflow calls too -- the whole point being that CI and a local run cannot disagree about what
// "72.4" means. What this adds is the entry point and the two things Gradle knows and a shell
// script does not: where the execution data is, and where the JaCoCo command line tool is.
//
// `doc/dev-guide/coverage.md` is the page; it also says what a local run cannot reproduce.
// ===================================================================================================================

val coverageExecDir: Directory = layout.buildDirectory.dir("coverage/exec").get()
val coverageReportDir: Directory = layout.buildDirectory.dir("coverage/reports").get()

// The command line tool, resolved by Gradle rather than downloaded by the script: it is then the
// exact artefact this build is locked to, and a local run needs no network. It is the `jacocoCli`
// configuration `ontrack-kdsl-acceptance` already declares for its container dumps (#1819) --
// a second one here would need its own lock state for no gain. Read through a provider because
// that project is evaluated after this script; by the time a task runs, it is there.
//
// `org.jacoco.cli` pulls `org.jacoco.core` and `org.jacoco.report` with it, and the dump task uses
// all three as a classpath. The script wants one `java -jar`, so it gets the `nodeps` artefact,
// which is the shaded one and needs nothing else.
val jacocoCliJar: Provider<String> = provider {
    project(":ontrack-kdsl-acceptance").configurations.getByName("jacocoCli")
        .single { it.name.startsWith("org.jacoco.cli") }
        .absolutePath
}

val coverageStage = tasks.register<Exec>("coverageStage") {
    group = "verification"
    description = "Lays this checkout's JaCoCo execution data out the way the CI artefacts are laid out"
    workingDir = rootDir
    commandLine(
        "$rootDir/scripts/coverage-report.sh", "stage",
        rootDir.absolutePath, coverageExecDir.asFile.absolutePath,
    )
    // Never up to date: the whole question is what the last test run left behind.
    outputs.upToDateWhen { false }
}

val coverageJacocoReport = tasks.register<Exec>("coverageJacocoReport") {
    group = "verification"
    description = "Builds the per-type and merged JaCoCo reports from the staged execution data"
    dependsOn(coverageStage)
    workingDir = rootDir
    commandLine(
        "$rootDir/scripts/coverage-report.sh", "report",
        coverageExecDir.asFile.absolutePath, coverageReportDir.asFile.absolutePath,
    )
    // The checkout itself, where CI passes the `coverage-classes` artefact: the classes that ran
    // locally are the ones in this build directory.
    environment("COVERAGE_TREE", rootDir.absolutePath)
    outputs.upToDateWhen { false }
    doFirst {
        environment("COVERAGE_JACOCO_CLI", jacocoCliJar.get())
    }
}

val coverageFigures = tasks.register<Exec>("coverageFigures") {
    group = "verification"
    description = "Prints the six sets of coverage figures from the reports"
    dependsOn(coverageJacocoReport)
    workingDir = rootDir
    commandLine("$rootDir/scripts/coverage-metrics.sh", coverageReportDir.asFile.absolutePath)
    outputs.upToDateWhen { false }
    // The figures are all-or-nothing by design -- coverage-metrics.sh emits no partial document --
    // and the commonest local reason to have none is a frontend suite that was never run. That is
    // not a reason to throw away the HTML reports that were just built, so it warns.
    isIgnoreExitValue = true
    doLast {
        if (executionResult.get().exitValue != 0) {
            logger.warn(
                "[coverage] no figures: scripts/coverage-metrics.sh failed. The commonest cause is a " +
                        "missing Jest summary -- run `./gradlew :ontrack-web-core:testCoverage -Pcoverage` " +
                        "for COVERAGE.UI_UNIT. The JaCoCo reports below are unaffected."
            )
        }
    }
}

tasks.register("coverageReport") {
    group = "verification"
    description = "Merges this checkout's coverage execution data into the same reports and figures as CI"
    dependsOn(coverageFigures)
    doLast {
        logger.lifecycle("[coverage] reports: ${coverageReportDir.asFile}")
        logger.lifecycle("[coverage]   merged, with its Sessions page: ${coverageReportDir.asFile}/merged/html/index.html")
        logger.lifecycle("[coverage] see doc/dev-guide/coverage.md for what these figures mean")
    }
}
