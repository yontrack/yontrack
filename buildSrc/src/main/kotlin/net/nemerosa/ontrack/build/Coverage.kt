package net.nemerosa.ontrack.build

/**
 * Test coverage collection, shared by everything that produces JaCoCo execution data (#1818, and
 * the rest of `initiative: coverage`).
 *
 * This is the **single place** where the JaCoCo version lives: the Gradle plugin's `toolVersion`
 * in the root build script reads [JACOCO_VERSION], and so must the agent mounted into the KDSL
 * and UI stacks (#1819) and the `jacococli` the report job runs (#1821). Nothing else in the
 * repository should spell a JaCoCo version out.
 *
 * Coverage is attributed **per test type**: every agent run tags its execution data with a
 * [session ID][sessionId] naming its type, and its shard when there is one, so that the merged
 * report's *Sessions* page shows where a class was covered from. See
 * `docs/grilling/2026-09-coverage/README.md`.
 */
object Coverage {

    /**
     * The JaCoCo version, for the Gradle plugin's `toolVersion` and for every jar the other
     * coverage issues need (`org.jacoco:org.jacoco.agent:<version>:runtime`,
     * `org.jacoco:org.jacoco.cli:<version>:nodeps`). 0.8.14 is the first release that reads JDK 25
     * class files, which is what this build produces.
     */
    const val JACOCO_VERSION = "0.8.14"

    /**
     * Gradle property gating the agent: `./gradlew test -Pcoverage`. The `jacoco` plugin itself is
     * applied unconditionally -- under the STRICT dependency locking of #1752 the configurations it
     * adds may not come and go with a property -- so this gates the *agent*, not the plugin.
     */
    const val GRADLE_PROPERTY = "coverage"

    /**
     * Environment variable naming the shard of the current run, appended to the session ID. The
     * build knows nothing of shards; sharding is a CI concept (the `integration` job runs 5 of
     * them), so the workflow sets this (#1821) and a local run leaves it unset.
     */
    const val SESSION_SUFFIX_ENV = "COVERAGE_SESSION_SUFFIX"

    /**
     * The test tasks whose *Gradle test JVM* is measured, each mapped to the session type naming
     * its origin.
     *
     * Deliberately a map rather than "every `Test` task": `kdslAcceptanceTest` also runs in a
     * Gradle JVM, but what it is there to cover is the backend running in a container, which is
     * collected by the agent inside that container (#1819). Measuring its client JVM as well would
     * produce execution data for the KDSL modules, which the reports exclude anyway.
     */
    private val SESSION_TYPES: Map<String, String> = mapOf(
        "test" to "unit",
        "integrationTest" to "integration",
    )

    /** Whether the Gradle JVM of this test task is measured at all. */
    fun collects(taskName: String): Boolean = taskName in SESSION_TYPES

    /**
     * The JaCoCo session ID for a test task, or `null` when that task is not collected here.
     *
     * @param taskName name of the `Test` task, e.g. `integrationTest`
     * @param suffix value of [SESSION_SUFFIX_ENV], usually the shard number; blank or `null` for a
     * run that is not sharded
     */
    fun sessionId(taskName: String, suffix: String?): String? {
        val type = SESSION_TYPES[taskName] ?: return null
        return join(type, suffix)
    }

    /** Where a test task's execution data goes, relative to the project's build directory. */
    fun execFilePath(taskName: String): String = "jacoco/$taskName.exec"

    // ===========================================================================================
    // The backend *container* of the KDSL and UI acceptance stacks (#1819)
    //
    // Here the code under test does not run in a Gradle JVM at all: it runs in the
    // `nemerosa/ontrack` container the three `compose/docker-compose-kdsl*.yml` files start. The
    // agent gets in through a coverage-only Compose override, never through the released image,
    // and its data is pulled over TCP by `jacococli dump` before the stack goes down.
    // ===========================================================================================

    /**
     * Environment variable the CI workflow uses to *name* the session type of the one Compose
     * variant two suites share, [SHARED_VARIANT].
     *
     * It exists because `kdslAcceptanceTest` is brought up both by the KDSL acceptance tests
     * (`kdsl`) and by the main Playwright leg (`ui-main`), and the variant alone cannot tell them
     * apart: the workflow says which one it is (#1821). A local run leaves it unset and gets the
     * default.
     *
     * It deliberately renames *only* that variant. Setting it once for a job that runs several
     * legs must not collapse `ui-ldap` and `ui-oidc` onto the same name -- they would then dump
     * over each other's `.exec`, and the merged report would lose a leg without saying so.
     */
    const val SESSION_OVERRIDE_ENV = "COVERAGE_SESSION"

    /**
     * The Compose variable through which the resolved session ID reaches
     * `compose/docker-compose-coverage.yml`, which puts it in the agent's `sessionid` option.
     */
    const val COMPOSE_SESSION_VARIABLE = "YONTRACK_COVERAGE_SESSION"

    /** Where the agent jar is staged, relative to the root project's build directory. */
    const val AGENT_JAR_PATH = "jacoco/jacocoagent.jar"

    /** Where the agent jar is mounted inside the backend container. */
    const val AGENT_JAR_CONTAINER_PATH = "/jacoco/jacocoagent.jar"

    /** The agent's `tcpserver` port *inside* the container; the host side is offset per slot. */
    const val AGENT_CONTAINER_PORT = 6300

    /**
     * The one Compose variant two suites share: the KDSL acceptance tests and the main Playwright
     * leg both run against `docker-compose-kdsl.yml`. It is the only variant
     * [SESSION_OVERRIDE_ENV] renames.
     */
    const val SHARED_VARIANT = "kdslAcceptanceTest"

    /**
     * The Compose variant of each acceptance stack, mapped to the session type it runs by default.
     *
     * `kdslLdap` and `kdslOidc` are only ever exercised by Playwright, so their defaults are the
     * final names and nothing renames them. [SHARED_VARIANT] defaults to `kdsl` and is renamed to
     * `ui-main` by the Playwright job -- see [SESSION_OVERRIDE_ENV].
     */
    private val CONTAINER_SESSION_TYPES: Map<String, String> = mapOf(
        SHARED_VARIANT to "kdsl",
        "kdslLdap" to "ui-ldap",
        "kdslOidc" to "ui-oidc",
    )

    /**
     * The JaCoCo session ID of the backend container of an acceptance stack.
     *
     * @param variant the Compose variant, e.g. `kdslOidc`
     * @param override value of [SESSION_OVERRIDE_ENV]; honoured for [SHARED_VARIANT] only
     * @param suffix value of [SESSION_SUFFIX_ENV], usually the shard number
     * @return the session ID, or `null` when the variant is not one of the three
     */
    fun containerSessionId(variant: String, override: String?, suffix: String?): String? {
        val default = CONTAINER_SESSION_TYPES[variant] ?: return null
        val type = if (variant == SHARED_VARIANT) slug(override) ?: default else default
        return join(type, suffix)
    }

    /**
     * Where a container run's execution data is dumped, relative to the *root* project's build
     * directory -- next to the agent jar, so that one directory holds the whole of what the
     * acceptance stacks contribute and #1821 has one place to look.
     *
     * Named after the session rather than after the variant, because the `kdslAcceptanceTest`
     * variant produces `kdsl` on one job and `ui-main-2` on another, and the two must not
     * overwrite each other.
     */
    fun containerExecFilePath(sessionId: String): String = "jacoco/$sessionId.exec"

    private fun join(type: String, suffix: String?): String =
        slug(suffix)?.let { "$type-$it" } ?: type

    /** Lowercases and slugifies a fragment; `null` for anything that is blank or only separators. */
    private fun slug(value: String?): String? =
        value?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]+"), "-")?.trim('-')?.takeIf { it.isNotEmpty() }
}
