package net.nemerosa.ontrack.extension.gitlab.autoversioning

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingFailureException
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelineCompletion
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelineStatus
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelineTrigger
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelinesService
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitLabPostProcessingTest {

    private val defaultConfiguration = GitLabConfiguration(
        name = "default-config",
        url = "https://gitlab.com",
        token = "secret-token",
    )

    private val otherConfiguration = GitLabConfiguration(
        name = "other-config",
        url = "https://gitlab.example.com",
        token = "other-secret-token",
    )

    private val completeSettings = GitLabPostProcessingSettings(
        config = "default-config",
        project = "default-group/default-project",
        ref = "release/\${VERSION}",
        retries = 5,
        retriesDelaySeconds = 20,
    )

    private val emptySettings = GitLabPostProcessingSettings(
        config = "",
        project = "",
    )

    private class FakePipelinesService(
        private val status: String = "success",
        private val timedOut: Boolean = false,
    ) : GitLabPipelinesService {

        var triggered: Triggered? = null
        var waited: Waited? = null

        data class Triggered(
            val configuration: String,
            val project: String,
            val ref: String,
            val variables: Map<String, String>,
        )

        data class Waited(val pipelineId: Long, val timeout: Duration, val interval: Duration)

        override fun trigger(
            configuration: GitLabConfiguration,
            project: String,
            ref: String,
            variables: Map<String, String>,
        ): GitLabPipelineTrigger {
            triggered = Triggered(configuration.name, project, ref, variables)
            return GitLabPipelineTrigger(id = 42, iid = 7, url = PIPELINE_URL, status = "created")
        }

        override fun waitForCompletion(
            configuration: GitLabConfiguration,
            project: String,
            pipelineId: Long,
            timeout: Duration,
            interval: Duration,
            progress: (status: GitLabPipelineStatus) -> Unit,
        ): GitLabPipelineCompletion {
            waited = Waited(pipelineId, timeout, interval)
            return GitLabPipelineCompletion(
                status = GitLabPipelineStatus(
                    id = pipelineId,
                    iid = 7,
                    url = PIPELINE_URL,
                    status = if (timedOut) "running" else status,
                    completed = !timedOut,
                ),
                timedOut = timedOut,
            )
        }
    }

    /**
     * Replaces `${VERSION}` by `2.0.0`, enough to check what is templated.
     */
    private val renderer = object : AutoVersioningTemplateRenderer {
        override fun render(template: String, renderer: EventRenderer): String =
            template.replace("\${VERSION}", "2.0.0")
    }

    private fun postProcessing(
        settings: GitLabPostProcessingSettings = completeSettings,
        service: GitLabPipelinesService = FakePipelinesService(),
    ): GitLabPostProcessing {
        val cachedSettingsService = mockk<CachedSettingsService>()
        every { cachedSettingsService.getCachedSettings(GitLabPostProcessingSettings::class.java) } returns settings
        val configurationService = mockk<GitLabConfigurationService>()
        every { configurationService.findConfiguration(any()) } returns null
        every { configurationService.findConfiguration("default-config") } returns defaultConfiguration
        every { configurationService.findConfiguration("other-config") } returns otherConfiguration
        return GitLabPostProcessing(
            extensionFeature = mockk(),
            cachedSettingsService = cachedSettingsService,
            gitLabConfigurationService = configurationService,
            gitLabPipelinesService = service,
        )
    }

    private fun run(
        postProcessing: GitLabPostProcessing,
        config: GitLabPostProcessingConfig,
    ): List<PostProcessingInfo> {
        val order = mockk<AutoVersioningOrder>()
        every { order.targetVersion } returns "2.0.0"
        val infos = mutableListOf<PostProcessingInfo>()
        postProcessing.postProcessing(
            config = config,
            autoVersioningOrder = order,
            repositoryURI = "https://gitlab.com/target-group/target-project.git",
            repository = "target-group/target-project",
            upgradeBranch = "feature/auto-upgrade-2.0.0",
            scm = mockk(),
            avTemplateRenderer = renderer,
        ) { infos += it }
        return infos
    }

    private val sampleConfig = GitLabPostProcessingConfig(
        dockerImage = "gradle:\${VERSION}",
        dockerCommand = "./gradlew dependencies --write-locks",
        commitMessage = "Locks for \${VERSION}",
    )

    @Test
    fun `Settings are used when the order does not override them`() {
        val service = FakePipelinesService()
        run(postProcessing(service = service), sampleConfig)
        val triggered = service.triggered!!
        assertEquals("default-config", triggered.configuration)
        assertEquals("default-group/default-project", triggered.project)
        assertEquals("release/2.0.0", triggered.ref, "Ref from the settings is templated")
    }

    @Test
    fun `Order configuration overrides the settings`() {
        val service = FakePipelinesService()
        run(
            postProcessing(service = service),
            sampleConfig.copy(
                config = "other-config",
                project = "group/project",
                ref = "develop",
            )
        )
        val triggered = service.triggered!!
        assertEquals("other-config", triggered.configuration)
        assertEquals("group/project", triggered.project)
        assertEquals("develop", triggered.ref)
    }

    @Test
    fun `Default ref is main`() {
        val service = FakePipelinesService()
        run(
            postProcessing(
                settings = GitLabPostProcessingSettings(
                    config = "default-config",
                    project = "group/project",
                ),
                service = service,
            ),
            sampleConfig,
        )
        assertEquals("main", service.triggered?.ref)
    }

    @Test
    fun `Variables sent to the pipeline are uppercase`() {
        val service = FakePipelinesService()
        run(postProcessing(service = service), sampleConfig)
        assertEquals(
            mapOf(
                "REPOSITORY" to "https://gitlab.com/target-group/target-project.git",
                "UPGRADE_BRANCH" to "feature/auto-upgrade-2.0.0",
                "DOCKER_IMAGE" to "gradle:2.0.0",
                "DOCKER_COMMAND" to "./gradlew dependencies --write-locks",
                "COMMIT_MESSAGE" to "Locks for 2.0.0",
                "VERSION" to "2.0.0",
            ),
            service.triggered?.variables
        )
    }

    @Test
    fun `No credential is ever sent as a pipeline variable`() {
        val service = FakePipelinesService()
        run(postProcessing(service = service), sampleConfig)
        val variables = service.triggered?.variables ?: error("Not triggered")
        assertTrue(
            variables.values.none { it.contains("secret-token") },
            "The GitLab token never reaches a pipeline variable"
        )
    }

    @Test
    fun `Missing Docker fields are sent as empty variables`() {
        val service = FakePipelinesService()
        run(postProcessing(service = service), GitLabPostProcessingConfig())
        val variables = service.triggered?.variables ?: error("Not triggered")
        assertEquals("", variables["DOCKER_IMAGE"])
        assertEquals("", variables["DOCKER_COMMAND"])
        assertEquals("", variables["COMMIT_MESSAGE"])
    }

    @Test
    fun `Successful pipeline reports its URL and waits using the retries of the settings`() {
        val service = FakePipelinesService()
        val infos = run(postProcessing(service = service), sampleConfig)
        assertEquals(listOf(PostProcessingInfo(data = mapOf("url" to PIPELINE_URL))), infos)
        assertEquals(
            FakePipelinesService.Waited(
                pipelineId = 42,
                timeout = Duration.ofSeconds(100),
                interval = Duration.ofSeconds(20),
            ),
            service.waited
        )
    }

    @Test
    fun `Failed pipeline fails the post-processing with a link to the pipeline`() {
        val service = FakePipelinesService(status = "failed")
        val ex = assertFailsWith<GitLabPostProcessingFailureException> {
            run(postProcessing(service = service), sampleConfig)
        }
        assertTrue(ex is PostProcessingFailureException)
        assertEquals(PIPELINE_URL, ex.link)
        assertEquals("GitLab pipeline $PIPELINE_URL completed with status failed.", ex.message)
    }

    @Test
    fun `Canceled pipeline fails the post-processing`() {
        val service = FakePipelinesService(status = "canceled")
        val ex = assertFailsWith<GitLabPostProcessingFailureException> {
            run(postProcessing(service = service), sampleConfig)
        }
        assertEquals("GitLab pipeline $PIPELINE_URL completed with status canceled.", ex.message)
    }

    @Test
    fun `Pipeline not completing in time fails the post-processing`() {
        val service = FakePipelinesService(timedOut = true)
        val ex = assertFailsWith<GitLabPostProcessingFailureException> {
            run(postProcessing(service = service), sampleConfig)
        }
        assertEquals(PIPELINE_URL, ex.link)
        assertEquals(
            "GitLab pipeline $PIPELINE_URL did not complete in 100 seconds (last status: running).",
            ex.message
        )
    }

    @Test
    fun `Validation accepts a missing configuration when the settings are complete`() {
        val config = postProcessing().parseAndValidate(null)
        assertNull(config.config)
    }

    @Test
    fun `Validation parses the order configuration`() {
        val config = postProcessing(settings = emptySettings).parseAndValidate(
            mapOf(
                "dockerImage" to "image",
                "config" to "other-config",
                "project" to "group/project",
                "ref" to "develop",
            ).asJson()
        )
        assertEquals("image", config.dockerImage)
        assertEquals("group/project", config.project)
        assertEquals("develop", config.ref)
    }

    @Test
    fun `Validation rejects a missing GitLab configuration`() {
        val ex = assertFailsWith<GitLabPostProcessingConfigException> {
            postProcessing(settings = emptySettings).parseAndValidate(
                mapOf("project" to "group/project").asJson()
            )
        }
        assertEquals(
            "No GitLab configuration is defined for the GitLab post-processing, neither in the order nor in the settings.",
            ex.message
        )
    }

    @Test
    fun `Validation rejects an unknown GitLab configuration`() {
        val ex = assertFailsWith<GitLabPostProcessingConfigException> {
            postProcessing().parseAndValidate(mapOf("config" to "unknown").asJson())
        }
        assertEquals("Cannot find GitLab configuration with name: unknown", ex.message)
    }

    @Test
    fun `Validation rejects a missing project`() {
        val ex = assertFailsWith<GitLabPostProcessingConfigException> {
            postProcessing(settings = emptySettings).parseAndValidate(
                mapOf("config" to "other-config").asJson()
            )
        }
        assertEquals(
            "No project is defined for the GitLab post-processing, neither in the order nor in the settings.",
            ex.message
        )
    }

    @Test
    fun `Post-processing is not triggered when the configuration is incomplete`() {
        val service = FakePipelinesService()
        assertFailsWith<GitLabPostProcessingConfigException> {
            run(postProcessing(settings = emptySettings, service = service), sampleConfig)
        }
        assertNull(service.triggered)
    }

    companion object {
        private const val PIPELINE_URL = "https://gitlab.com/default-group/default-project/-/pipelines/42"
    }
}
