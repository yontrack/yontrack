import com.avast.gradle.dockercompose.ComposeExtension
import net.nemerosa.ontrack.build.Coverage
import net.nemerosa.ontrack.build.KdslStack
import net.nemerosa.ontrack.build.KdslStackInstance

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
    testImplementation("com.apollographql.apollo:apollo-api:5.2.0")
    testImplementation("commons-io:commons-io")
    testImplementation("commons-codec:commons-codec")

    testImplementation("org.influxdb:influxdb-java")
    testImplementation(testFixtures(project(":ontrack-extension-github")))

    // Already on the test runtime classpath for every module; needed at compile time here for
    // ShardingFilter, which implements one of its interfaces.
    testImplementation("org.junit.platform:junit-platform-launcher")
}

// Pre-acceptance tests: starting the environment

// The compose files resolve the Yontrack images as `nemerosa/ontrack:${ONTRACK_VERSION:-latest}`, so
// leaving this unset silently runs the acceptance tests against whatever `latest` happens to be. Locally
// that is the image `dockerBuild` just tagged, i.e. the project version. On CI the images are built on
// another runner and restored under a known tag, so the workflow names it through ONTRACK_VERSION.
val ontrackVersion: String = System.getenv("ONTRACK_VERSION")?.takeIf { it.isNotBlank() }
    ?: project.version.toString()

// The acceptance stack is an *instance* of this checkout, the way the
// development and integration test stacks are: its Compose projects and every
// port it publishes are derived from a slot, so that two worktrees can run the
// acceptance tests at the same time. The main working copy takes slot 0 and
// keeps the historical ports -- which is what every CI runner, a fresh clone,
// also gets. The three variants share one slot: they are sequenced below so
// that they are never up at the same time.
// See docs/adr/0013-parallel-kdsl-acceptance-stacks.md.
//
// Claiming the slot probes the ports of every slot and fails when none is
// free, so it happens at *execution* time, not here. This file is configured
// by every Gradle invocation in the checkout, and a build that is never going
// to run the acceptance tests -- `:ontrack-extension-x:integrationTest`, say --
// used to die on a "no free slot" raised by a stack it does not touch. Naming
// the three Compose projects costs nothing and stays eager; everything derived
// from the slot goes through `kdslStack`, which resolves once, lazily, and
// therefore reports a failure against the task that asked for it.
val kdslNames = KdslStack.names(rootDir)
val kdslStack: KdslStackInstance by lazy { KdslStack.resolve(rootDir) }
val kdslComposeEnvironment: Provider<Map<String, String>> = provider { kdslStack.composeEnvironment }

// Test coverage of the backend *container* (#1819).
//
// The code the acceptance and Playwright suites exercise does not run in a Gradle JVM: it runs in
// the `nemerosa/ontrack` container these stacks start. The agent gets in through
// `compose/docker-compose-coverage.yml`, a coverage-only override of the backend service -- the
// released image is never touched -- and its data is pulled out over TCP before the stack goes
// down, by the dump tasks further below.
//
// Everything here is gated on `-Pcoverage`: without it the task graph, the Compose files and the
// containers are exactly what they were.
val coverage = providers.gradleProperty(Coverage.GRADLE_PROPERTY).isPresent

// The session type of the container, per Compose variant. `kdslAcceptanceTest` is brought up both
// by the KDSL suite (`kdsl`) and by the main Playwright leg (`ui-main`), which is why the type can
// be overridden from the environment and is not read off the variant alone. See `Coverage`.
val coverageSessionOverride = providers.environmentVariable(Coverage.SESSION_OVERRIDE_ENV).orNull
val coverageSessionSuffix = providers.environmentVariable(Coverage.SESSION_SUFFIX_ENV).orNull

fun coverageSessionId(variant: String): String =
    Coverage.containerSessionId(variant, coverageSessionOverride, coverageSessionSuffix)
        ?: error("No coverage session for the Compose variant '$variant'")

/**
 * The Compose files of one acceptance variant: its base file, plus the coverage override under
 * `-Pcoverage`.
 */
fun composeFilesOf(baseFile: String): List<String> = listOfNotNull(
    "${rootDir}/compose/$baseFile",
    "${rootDir}/compose/docker-compose-coverage.yml".takeIf { coverage },
)

configure<ComposeExtension> {
    createNested("kdslAcceptanceTest").apply {
        useComposeFiles.addAll(composeFilesOf("docker-compose-kdsl.yml"))
        setProjectName(kdslNames.projectName)
        environment.put("ONTRACK_VERSION", ontrackVersion)
        environment.put(Coverage.COMPOSE_SESSION_VARIABLE, coverageSessionId("kdslAcceptanceTest"))
        environment.putAll(kdslComposeEnvironment)
        captureContainersOutput.set(true)
        captureContainersOutputToFiles.set(file("build/logs/kdsl/containers"))
        composeLogToFile.set(file("build/logs/kdsl/compose"))
        retainContainersOnStartupFailure.set(true)
    }
    createNested("kdslLdap").apply {
        useComposeFiles.addAll(composeFilesOf("docker-compose-kdsl-ldap.yml"))
        setProjectName(kdslNames.ldapProjectName)
        environment.put("ONTRACK_VERSION", ontrackVersion)
        environment.put(Coverage.COMPOSE_SESSION_VARIABLE, coverageSessionId("kdslLdap"))
        environment.putAll(kdslComposeEnvironment)
        captureContainersOutput.set(true)
        captureContainersOutputToFiles.set(file("build/logs/kdsl-ldap/containers"))
        composeLogToFile.set(file("build/logs/kdsl-ldap/compose"))
        retainContainersOnStartupFailure.set(true)
    }
    createNested("kdslOidc").apply {
        useComposeFiles.addAll(composeFilesOf("docker-compose-kdsl-oidc.yml"))
        setProjectName(kdslNames.oidcProjectName)
        environment.put("ONTRACK_VERSION", ontrackVersion)
        environment.put(Coverage.COMPOSE_SESSION_VARIABLE, coverageSessionId("kdslOidc"))
        environment.putAll(kdslComposeEnvironment)
        captureContainersOutput.set(true)
        captureContainersOutputToFiles.set(file("build/logs/kdsl-oidc/containers"))
        composeLogToFile.set(file("build/logs/kdsl-oidc/compose"))
        retainContainersOnStartupFailure.set(true)
    }
}

val isCI = System.getenv("CI") == "true"

// The one place the slot is claimed on purpose, rather than as a side effect of
// a Compose task reading its ports. Every acceptance task runs it first, so a
// checkout with no free slot fails here, with the message as it is written --
// rather than inside Gradle's provider machinery, which buries it under three
// layers of "Failed to query the value of property 'environment'".
val kdslStackSlot by tasks.registering {
    group = "verification"
    description = "Claims this checkout's KDSL acceptance slot and records it in ${KdslStack.INSTANCE_ENV_PATH}"
    doFirst {
        // Recorded before the stack comes up rather than after, so that the
        // ports are discoverable even when it fails to start -- and it is
        // then that they are most wanted.
        kdslStack.writeInstanceEnv(rootProject.file(KdslStack.INSTANCE_ENV_PATH))
        logger.lifecycle("[kdsl-stack] ${kdslStack.describe()}")
    }
}

// Both the build and the up task of each variant read the ports, and Compose
// builds before it starts, so the claim has to come before the earliest of them.
listOf("kdslAcceptanceTest", "kdslLdap", "kdslOidc").forEach { variant ->
    listOf("ComposeBuild", "ComposeUp").forEach { phase ->
        tasks.named("$variant$phase") {
            dependsOn(kdslStackSlot)
        }
    }
}

// The images are tagged with the project version, so a local run must build them first -- each
// variant on its own, since `uiLdapTest` and `uiOidcTest` start their stack without the main one.
listOf("kdslAcceptanceTest", "kdslLdap", "kdslOidc").forEach { variant ->
    tasks.named("${variant}ComposeUp") {
        if (!isCI) {
            dependsOn(":ontrack-ui:dockerBuild")
            dependsOn(":ontrack-web-core:dockerBuild")
        }
    }
}

val kdslAcceptanceTestComposeUp by tasks.named("kdslAcceptanceTestComposeUp")

// ===================================================================================================================
// Test coverage of the backend container (#1819)
// ===================================================================================================================

// The agent jar and the command line tool. `org.jacoco.agent` with the `runtime` classifier *is*
// the agent -- the plain artifact is a wrapper jar containing it -- and `org.jacoco.cli` with
// `nodeps` is the shaded command line tool. Both take their version from `Coverage`, which is the
// single place a JaCoCo version is spelled out in this repository.
//
// Two configurations of their own rather than the `jacocoAgent` the plugin already adds: that one
// holds the wrapper, and would have to be unpacked. New configurations are locked like any other,
// so a change here needs `./gradlew :ontrack-kdsl-acceptance:resolveAndLockAll --write-locks`.
val jacocoAgentRuntime: Configuration = configurations.create("jacocoAgentRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val jacocoCli: Configuration = configurations.create("jacocoCli") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add(jacocoAgentRuntime.name, "org.jacoco:org.jacoco.agent:${Coverage.JACOCO_VERSION}:runtime")
    add(jacocoCli.name, "org.jacoco:org.jacoco.cli:${Coverage.JACOCO_VERSION}:nodeps")
}

// Where the override expects the jar: `compose/docker-compose-coverage.yml` bind-mounts
// `../build/jacoco/jacocoagent.jar`, which Compose resolves against the directory of the base
// file, i.e. the repository root's build directory.
val coverageAgentJar by tasks.registering(Copy::class) {
    group = "verification"
    description = "Stages the JaCoCo agent jar where the Compose coverage override mounts it from"
    from(jacocoAgentRuntime)
    into(rootProject.layout.buildDirectory.dir(Coverage.AGENT_JAR_PATH.substringBeforeLast('/')))
    rename { Coverage.AGENT_JAR_PATH.substringAfterLast('/') }
}

// Pulling the data out.
//
// `output=tcpserver` writes nothing on JVM exit, which is the point: the data survives whatever
// stops the container, and is fetched over TCP while it is still up. So each variant gets a dump
// task that runs after its test task and *before* its ComposeDown -- and, because the three
// variants are sequenced (`kdslLdapComposeUp` waits for `kdslAcceptanceTestComposeDown`), before
// the next variant's stack comes up as well.
//
// Each dump is also a task the CI workflow can call on its own (#1821): under `isCI` the workflow
// brings the stacks up and down itself, so nothing here may be the only thing that triggers it.
val coverageDumpTasks: Map<String, TaskProvider<JavaExec>> =
    listOf("kdslAcceptanceTest", "kdslLdap", "kdslOidc").associateWith { variant ->
        val sessionId = coverageSessionId(variant)
        val destFile = rootProject.layout.buildDirectory.file(Coverage.containerExecFilePath(sessionId))
        tasks.register<JavaExec>("${variant}CoverageDump") {
            group = "verification"
            description = "Dumps the JaCoCo execution data of the $variant backend container as session '$sessionId'"
            // Never on a build that did not ask for coverage: there is no agent listening then, and
            // the dump would hang until it gave up.
            onlyIf { coverage }
            dependsOn(kdslStackSlot)
            classpath = jacocoCli
            mainClass.set("org.jacoco.cli.internal.Main")
            outputs.file(destFile)
            outputs.upToDateWhen { false }
            // A dump that cannot reach the agent -- a container that died, a stack somebody else
            // already stopped -- must not turn a green test run red: collecting coverage is not
            // what the suite is there to prove. The missing file is the signal, and the report job
            // turns it into a FAILED COVERAGE.* stamp rather than a failed build (#1821, #1822).
            isIgnoreExitValue = true
            doLast {
                if (!destFile.get().asFile.isFile) {
                    logger.warn(
                        "[coverage] no execution data dumped from the $variant backend container; " +
                                "${destFile.get().asFile} was not written"
                    )
                }
            }
            argumentProviders.add(CommandLineArgumentProvider {
                destFile.get().asFile.parentFile.mkdirs()
                listOf(
                    "dump",
                    "--address", "localhost",
                    // Never 6300: the agent port is offset per slot like every other published port
                    // of these stacks, and comes from KdslStack.
                    "--port", kdslStack.jacocoPort.toString(),
                    // The container may still be starting when a standalone dump is asked for.
                    "--retry", "10",
                    "--destfile", destFile.get().asFile.absolutePath,
                )
            })
        }
    }

if (coverage) {
    // The jar has to be on disk before Compose mounts it, or Docker creates a directory in its
    // place and the JVM refuses to start.
    listOf("kdslAcceptanceTest", "kdslLdap", "kdslOidc").forEach { variant ->
        tasks.named("${variant}ComposeUp") {
            dependsOn(coverageAgentJar)
        }
        // After the test task, before the stack goes down.
        tasks.named("${variant}ComposeDown") {
            mustRunAfter(coverageDumpTasks.getValue(variant))
        }
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
    // Never restored from the build cache (#1870): the suite tests a running instance, which is
    // not one of its inputs.
    outputs.cacheIf("acceptance tests run against a live instance") { false }
    include("**/ACC*.class")
    val testFilter = System.getProperty("test.filter")
    if (testFilter != null) {
        filter {
            includeTestsMatching(testFilter)
        }
    }
    // Splitting the suite across CI runners. The partition itself is computed inside the test JVM by
    // ShardingFilter, from the classes the launcher actually discovered — nothing here enumerates
    // them. Left at 1 of 1 when unset, which is every local run, and the filter is then inert.
    systemProperty("shard.index", System.getProperty("shard.index") ?: "1")
    systemProperty("shard.total", System.getProperty("shard.total") ?: "1")

    // Point the suite at this checkout's instance of the stack. Without these
    // the ACCProperties defaults -- localhost:8080 and friends -- would send
    // every worktree to the same containers. An explicitly provided value
    // still wins, so a run against an instance elsewhere keeps working.
    // Contributed as an argument provider rather than as plain system
    // properties so that the slot is claimed when the task runs rather than
    // when this file is configured.
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        kdslStack.systemProperties.map { (key, value) ->
            "-D$key=${System.getProperty(key) ?: value}"
        }
    })
    minHeapSize = "512m"
    maxHeapSize = "3072m"
    dependsOn(kdslAcceptanceTestComposeUp)
    if (coverage) {
        // Whether or not this run owns the stack: the data has to leave the container before
        // anything else can stop it.
        finalizedBy(coverageDumpTasks.getValue("kdslAcceptanceTest"))
    }
    if (!isCI) {
        finalizedBy(kdslAcceptanceTestComposeDown)
    }
}
