package net.nemerosa.ontrack.extension.gitlab

import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Checks the content the wizard uploads to the fixture project of the GitLab test group.
 *
 * See `ontrack-extension-gitlab/scripts/gitlab-test-project.sh` and the module's README.
 */
class GitLabTestFixtureTest {

    /**
     * Top-level keywords of a `.gitlab-ci.yml` which are not jobs.
     */
    private val globalKeywords = setOf(
        "default", "include", "stages", "variables", "workflow",
        "image", "services", "before_script", "after_script", "cache",
    )

    private fun resource(name: String): String =
        GitLabTestFixture::class.java.getResourceAsStream("${GitLabTestFixture.RESOURCE_DIR}/$name")
            ?.use { it.reader().readText() }
            ?: error("Missing fixture resource $name")

    @Suppress("UNCHECKED_CAST")
    private val ci: Map<String, Any?> by lazy {
        Yaml().load<Map<String, Any?>>(resource(GitLabTestFixture.CI_FILE))
    }

    @Suppress("UNCHECKED_CAST")
    private val mockJob: Map<String, Any?>
        get() = ci[GitLabTestFixture.JOB_MOCK] as? Map<String, Any?>
            ?: error("No ${GitLabTestFixture.JOB_MOCK} job in ${GitLabTestFixture.CI_FILE}")

    private val mockScript: String
        get() = script(GitLabTestFixture.JOB_MOCK)

    @Suppress("UNCHECKED_CAST")
    private fun job(name: String): Map<String, Any?> =
        ci[name] as? Map<String, Any?> ?: error("No $name job in ${GitLabTestFixture.CI_FILE}")

    private fun script(name: String): String =
        (job(name)["script"] as? List<*>)?.joinToString("\n")
            ?: error("No script in the $name job")

    private fun ifRules(rules: Any?): List<String> =
        (rules as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("if")?.toString() }
            ?: error("No rules")

    private val avScript: String
        get() = script(GitLabTestFixture.JOB_AV)

    @Test
    fun `No pipeline runs unless a test asks for one, so that pushes consume no compute minutes`() {
        @Suppress("UNCHECKED_CAST")
        val rules = ci["workflow"].let { it as? Map<String, Any?> }?.get("rules") as? List<*>
            ?: error("No workflow rules in ${GitLabTestFixture.CI_FILE}")
        // The only ways in are the two trigger variables, one per job...
        assertEquals(
            listOf(
                "\$${GitLabTestFixture.VARIABLE_RESULT}",
                "\$${GitLabTestFixture.VARIABLE_UPGRADE_BRANCH}",
            ),
            ifRules(rules),
        )
        // ... and everything else is turned away.
        assertEquals("never", (rules.last() as? Map<*, *>)?.get("when"))
    }

    @Test
    fun `The mock and auto-versioning jobs are the only jobs`() {
        assertEquals(
            setOf(GitLabTestFixture.JOB_MOCK, GitLabTestFixture.JOB_AV),
            ci.keys - globalKeywords,
        )
    }

    @Test
    fun `Each job runs on its own trigger variable, so that one test never starts the other job`() {
        assertEquals(
            listOf("\$${GitLabTestFixture.VARIABLE_RESULT}"),
            ifRules(mockJob["rules"]),
        )
        assertEquals(
            listOf("\$${GitLabTestFixture.VARIABLE_UPGRADE_BRANCH}"),
            ifRules(job(GitLabTestFixture.JOB_AV)["rules"]),
        )
    }

    @Test
    fun `Every job is capped in time`() {
        listOf(GitLabTestFixture.JOB_MOCK, GitLabTestFixture.JOB_AV).forEach { name ->
            val timeout = job(name)["timeout"]?.toString() ?: error("No timeout in the $name job")
            val minutes = Regex("^(\\d+) minutes?$").matchEntire(timeout)?.groupValues?.get(1)?.toInt()
            assertNotNull(minutes, "Expected a timeout in minutes for $name, got '$timeout'")
            assertTrue(minutes in 1..10, "Expected a timeout of 10 minutes at most for $name, got '$timeout'")
        }
    }

    @Test
    fun `The auto-versioning job requires every variable the post-processing sends`() {
        GitLabTestFixture.POST_PROCESSING_VARIABLES.forEach { variable ->
            assertTrue(
                avScript.contains(variable),
                "The ${GitLabTestFixture.JOB_AV} job does not check $variable",
            )
        }
        assertTrue(
            avScript.contains("exit 1"),
            "The ${GitLabTestFixture.JOB_AV} job does not fail on a missing variable",
        )
    }

    @Test
    fun `The auto-versioning job runs the command it is given, which is how a test asks it to fail`() {
        assertTrue(
            avScript.contains("sh -c") && avScript.contains(GitLabTestFixture.VARIABLE_DOCKER_COMMAND),
            "The ${GitLabTestFixture.JOB_AV} job does not run ${GitLabTestFixture.VARIABLE_DOCKER_COMMAND}",
        )
    }

    @Test
    fun `The mock job takes the duration it is asked for`() {
        assertTrue(
            mockScript.contains("sleep") && mockScript.contains(GitLabTestFixture.VARIABLE_DURATION),
            "The mock job does not honour ${GitLabTestFixture.VARIABLE_DURATION}",
        )
    }

    @Test
    fun `The mock job fails when it is asked to`() {
        assertTrue(
            mockScript.contains(GitLabTestFixture.RESULT_FAILURE) && mockScript.contains("exit 1"),
            "The mock job does not fail on ${GitLabTestFixture.VARIABLE_RESULT}=${GitLabTestFixture.RESULT_FAILURE}",
        )
    }

    @Test
    fun `The mock job echoes its message`() {
        assertTrue(
            mockScript.contains(GitLabTestFixture.VARIABLE_MESSAGE),
            "The mock job does not echo ${GitLabTestFixture.VARIABLE_MESSAGE}",
        )
    }

    @Test
    fun `The version file has a version`() {
        val properties = Properties()
        properties.load(resource(GitLabTestFixture.VERSION_FILE).reader())
        assertNotNull(properties.getProperty(GitLabTestFixture.VERSION_PROPERTY))
    }

}
