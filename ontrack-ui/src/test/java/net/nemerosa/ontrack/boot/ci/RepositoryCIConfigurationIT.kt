package net.nemerosa.ontrack.boot.ci

import net.nemerosa.ontrack.extension.config.ci.CIConfigurationService
import net.nemerosa.ontrack.extension.config.model.CIEnv
import net.nemerosa.ontrack.boot.ui.AbstractWebTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Computes the effective configuration of Yontrack's own `.yontrack/ci.yaml` - the file at the
 * root of this repository, not a copy - for the branches that build.
 *
 * The environments and their slots are declared from `main` only (#2120). The slots of a project
 * are resynced with `syncForward`, which deletes those the incoming configuration does not
 * mention, so any other branch declaring them from its own, older copy of the file tears slots
 * down or rewrites their rules - which is how `release/5.5` brought the v6 slot back (#2119).
 *
 * In this module because the file uses the CI-config extensions of several of them -
 * `autoVersioning`, `notificationsConfig`, `environments` - and every extension is on the test
 * runtime classpath of `ontrack-ui`. The environments one is not on its compile classpath, hence
 * the assertions on JSON.
 */
class RepositoryCIConfigurationIT : AbstractWebTestSupport() {

    @Autowired
    private lateinit var ciConfigurationService: CIConfigurationService

    private val ciYaml: String by lazy {
        val root = File("..").canonicalFile
        assertTrue(File(root, "settings.gradle.kts").exists(), "Root of the build found at $root")
        File(root, ".yontrack/ci.yaml").readText()
    }

    /**
     * The `environments` extension of the project configuration computed for [scmBranch], from
     * the GitHub Actions environment `ci.yml` registers its builds from. The engines are named
     * rather than detected: detecting the SCM needs a GitHub configuration matching the
     * repository URL, which nothing here needs otherwise.
     */
    private fun environments(scmBranch: String): JsonNode? =
        ciConfigurationService.effectiveCIConfiguration(
            config = ciYaml,
            ci = "github",
            scm = "github",
            env = listOf(
                CIEnv("GITHUB_ACTIONS", "true"),
                CIEnv("GITHUB_SERVER_URL", "https://github.com"),
                CIEnv("GITHUB_REPOSITORY", "yontrack/yontrack"),
                CIEnv("GITHUB_WORKFLOW", "CI"),
                CIEnv("GITHUB_REF_NAME", scmBranch),
                CIEnv("GITHUB_RUN_ID", "1"),
                CIEnv("GITHUB_RUN_NUMBER", "1"),
                CIEnv("GITHUB_SHA", "7c0b1745f513b9162791651582c0044d7b6d2a83"),
                CIEnv("GITHUB_EVENT_NAME", "push"),
                CIEnv("VERSION", "6.0.0-rc.1"),
            ),
        ).configuration.project.extensions.find { it.id == "environments" }?.data

    @Test
    fun `The environments are declared by main`() {
        val config = assertNotNull(environments("main"), "main declares the environments")
        assertEquals(
            listOf("v5.dev.yontrack.com", "demo.dev.yontrack.com", "self.dev.yontrack.com"),
            config.path("environments").values()
                .sortedBy { it.path("order").asInt() }
                .map { it.path("name").asString() },
        )
    }

    @Test
    fun `The environments are not declared by release 5_5`() {
        assertNull(environments("release/5.5"), "release/5.5 leaves the environments and slots alone")
    }

    @Test
    fun `The environments are not declared by a feature branch`() {
        assertNull(
            environments("claude/some-feature-pipeline"),
            "A feature branch leaves the environments and slots alone"
        )
    }

    @Test
    fun `The v5 slot admits BRONZE builds of release-5_5 and is deployed by hand`() {
        val config = assertNotNull(environments("main"))

        val environment = config.path("environments").values()
            .single { it.path("name").asString() == "v5.dev.yontrack.com" }
        assertEquals(listOf("release"), environment.path("tags").values().map { it.asString() })

        val slot = config.path("slots").values()
            .single { it.path("project").asString() == "yontrack" }
            .path("environments").values()
            .single { it.path("name").asString() == "v5.dev.yontrack.com" }

        val rules = slot.path("admissionRules").values().associateBy { it.path("ruleId").asString() }
        assertEquals(setOf("branchPattern", "promotion"), rules.keys)
        assertEquals(
            listOf("^release-5\\.5$"),
            rules.getValue("branchPattern").path("ruleConfig").path("includes").values().map { it.asString() },
        )
        assertEquals("BRONZE", rules.getValue("promotion").path("ruleConfig").path("promotion").asString())

        val workflows = slot.path("workflows").values()
        assertEquals(
            listOf("RUNNING"),
            workflows.map { it.path("trigger").asString() },
            "No CANDIDATE workflow: a pipeline waits for someone to start the deployment"
        )
        val nodes = workflows.single().path("nodes").values().associateBy { it.path("id").asString() }
        fun parentsOf(id: String) = nodes.getValue(id).path("parents").values().map { it.path("id").asString() }

        val autoVersioning = nodes.getValue("auto-versioning").path("data")
        assertEquals("yontrack-infra-gitops", autoVersioning.path("targetProject").asString())
        assertEquals(
            "yontrack-helmfile.d/values/yontrack-v5/yontrack.yaml",
            autoVersioning.path("targetPath").asString()
        )
        assertEquals(
            "\${build.meta?name=rc-version&error=true}",
            autoVersioning.path("targetVersion").asString()
        )

        assertEquals(listOf("auto-versioning"), parentsOf("deploy"))

        assertEquals(listOf("deploy"), parentsOf("smoke"))
        val channelConfig = nodes.getValue("smoke").path("data").path("channelConfig")
        assertEquals("demo-smoke.yml", channelConfig.path("workflowId").asString())
        assertEquals("release/5.5", channelConfig.path("reference").asString())
        assertEquals(
            mapOf(
                "version" to "\${build.meta?name=rc-version&error=true}",
                "target" to "v5",
                "branch" to "release-5.5",
            ),
            channelConfig.path("inputs").values()
                .associate { it.path("name").asString() to it.path("value").asString() },
        )

        assertEquals(listOf("deploy"), parentsOf("notification"))
        val notification = nodes.getValue("notification").path("data")
        assertEquals("slack", notification.path("channel").asString())
        assertEquals("#notifications", notification.path("channelConfig").path("channel").asString())
    }

}
