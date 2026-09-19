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
     * `org.jacoco:org.jacoco.cli:<version>:nodeps`). 0.8.13 reads JDK 21 class files, which is what
     * this build produces.
     */
    const val JACOCO_VERSION = "0.8.13"

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
        val slug = suffix?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]+"), "-")?.trim('-')
        return if (slug.isNullOrEmpty()) type else "$type-$slug"
    }

    /** Where a test task's execution data goes, relative to the project's build directory. */
    fun execFilePath(taskName: String): String = "jacoco/$taskName.exec"
}
