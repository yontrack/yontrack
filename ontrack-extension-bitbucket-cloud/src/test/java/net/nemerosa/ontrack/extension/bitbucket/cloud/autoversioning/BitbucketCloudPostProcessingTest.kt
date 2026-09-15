package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingFailureException
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigMock
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelineCompletion
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelineStatus
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelineTrigger
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelinesService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventRenderer
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BitbucketCloudPostProcessingTest {

    private val defaultConfiguration = bitbucketCloudTestConfigMock(name = "default-config")
    private val otherConfiguration = bitbucketCloudTestConfigMock(name = "other-config")

    private val completeSettings = BitbucketCloudPostProcessingSettings(
        config = "default-config",
        workspace = "default-ws",
        repository = "default-repo",
        pipeline = "default-pipeline",
        branch = "release/\${VERSION}",
        retries = 5,
        retriesDelaySeconds = 20,
    )

    private val emptySettings = BitbucketCloudPostProcessingSettings(
        config = "",
        workspace = "",
        repository = "",
        pipeline = "",
    )

    private class FakePipelinesService(
        private val state: String = "SUCCESSFUL",
        private val timedOut: Boolean = false,
    ) : BitbucketPipelinesService {

        var triggered: Triggered? = null
        var waited: Waited? = null

        data class Triggered(
            val configuration: String,
            val workspace: String,
            val repository: String,
            val branch: String,
            val pipeline: String?,
            val variables: Map<String, String>,
        )

        data class Waited(val uuid: String, val timeout: Duration, val interval: Duration)

        override fun trigger(
            configuration: BitbucketCloudConfiguration,
            workspace: String,
            repository: String,
            branch: String,
            pipeline: String?,
            variables: Map<String, String>
        ): BitbucketPipelineTrigger {
            triggered = Triggered(configuration.name, workspace, repository, branch, pipeline, variables)
            return BitbucketPipelineTrigger(uuid = "{uuid}", buildNumber = 12, url = RUN_URL)
        }

        override fun waitForCompletion(
            configuration: BitbucketCloudConfiguration,
            workspace: String,
            repository: String,
            uuid: String,
            timeout: Duration,
            interval: Duration,
            progress: (status: BitbucketPipelineStatus) -> Unit
        ): BitbucketPipelineCompletion {
            waited = Waited(uuid, timeout, interval)
            return BitbucketPipelineCompletion(
                status = BitbucketPipelineStatus(
                    uuid = uuid,
                    buildNumber = 12,
                    url = RUN_URL,
                    state = if (timedOut) "IN_PROGRESS" else state,
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
        settings: BitbucketCloudPostProcessingSettings = completeSettings,
        service: BitbucketPipelinesService = FakePipelinesService(),
    ): BitbucketCloudPostProcessing {
        val cachedSettingsService = mockk<CachedSettingsService>()
        every { cachedSettingsService.getCachedSettings(BitbucketCloudPostProcessingSettings::class.java) } returns settings
        val configurationService = mockk<BitbucketCloudConfigurationService>()
        every { configurationService.findConfiguration(any()) } returns null
        every { configurationService.findConfiguration("default-config") } returns defaultConfiguration
        every { configurationService.findConfiguration("other-config") } returns otherConfiguration
        return BitbucketCloudPostProcessing(
            extensionFeature = mockk(),
            cachedSettingsService = cachedSettingsService,
            bitbucketCloudConfigurationService = configurationService,
            bitbucketPipelinesService = service,
        )
    }

    private fun run(
        postProcessing: BitbucketCloudPostProcessing,
        config: BitbucketCloudPostProcessingConfig,
    ): List<PostProcessingInfo> {
        val order = mockk<AutoVersioningOrder>()
        every { order.targetVersion } returns "2.0.0"
        val infos = mutableListOf<PostProcessingInfo>()
        postProcessing.postProcessing(
            config = config,
            autoVersioningOrder = order,
            repositoryURI = "https://bitbucket.org/target-ws/target-repo.git",
            repository = "target-ws/target-repo",
            upgradeBranch = "feature/auto-upgrade-2.0.0",
            scm = mockk(),
            avTemplateRenderer = renderer,
        ) { infos += it }
        return infos
    }

    private val sampleConfig = BitbucketCloudPostProcessingConfig(
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
        assertEquals("default-ws", triggered.workspace)
        assertEquals("default-repo", triggered.repository)
        assertEquals("default-pipeline", triggered.pipeline)
        assertEquals("release/2.0.0", triggered.branch, "Branch from the settings is templated")
    }

    @Test
    fun `Order configuration overrides the settings`() {
        val service = FakePipelinesService()
        run(
            postProcessing(service = service),
            sampleConfig.copy(
                config = "other-config",
                workspace = "ws",
                repository = "repo",
                pipeline = "pipeline",
                branch = "develop",
            )
        )
        val triggered = service.triggered!!
        assertEquals("other-config", triggered.configuration)
        assertEquals("ws", triggered.workspace)
        assertEquals("repo", triggered.repository)
        assertEquals("pipeline", triggered.pipeline)
        assertEquals("develop", triggered.branch)
    }

    @Test
    fun `Default branch is main`() {
        val service = FakePipelinesService()
        run(
            postProcessing(
                settings = BitbucketCloudPostProcessingSettings(
                    config = "default-config",
                    workspace = "ws",
                    repository = "repo",
                    pipeline = "pipeline",
                ),
                service = service,
            ),
            sampleConfig,
        )
        assertEquals("main", service.triggered?.branch)
    }

    @Test
    fun `Variables sent to the pipeline`() {
        val service = FakePipelinesService()
        run(postProcessing(service = service), sampleConfig)
        assertEquals(
            mapOf(
                "REPOSITORY" to "https://bitbucket.org/target-ws/target-repo.git",
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
    fun `Missing Docker fields are sent as empty variables`() {
        val service = FakePipelinesService()
        run(postProcessing(service = service), BitbucketCloudPostProcessingConfig())
        val variables = service.triggered?.variables ?: error("Not triggered")
        assertEquals("", variables["DOCKER_IMAGE"])
        assertEquals("", variables["DOCKER_COMMAND"])
        assertEquals("", variables["COMMIT_MESSAGE"])
    }

    @Test
    fun `Successful pipeline reports its URL and waits using the retries of the settings`() {
        val service = FakePipelinesService()
        val infos = run(postProcessing(service = service), sampleConfig)
        assertEquals(listOf(PostProcessingInfo(data = mapOf("url" to RUN_URL))), infos)
        assertEquals(
            FakePipelinesService.Waited(
                uuid = "{uuid}",
                timeout = Duration.ofSeconds(100),
                interval = Duration.ofSeconds(20),
            ),
            service.waited
        )
    }

    @Test
    fun `Failed pipeline fails the post-processing with a link to the run`() {
        val service = FakePipelinesService(state = "FAILED")
        val ex = assertFailsWith<BitbucketCloudPostProcessingFailureException> {
            run(postProcessing(service = service), sampleConfig)
        }
        assertTrue(ex is PostProcessingFailureException)
        assertEquals(RUN_URL, ex.link)
        assertEquals("Bitbucket pipeline $RUN_URL completed with state FAILED.", ex.message)
    }

    @Test
    fun `Pipeline not completing in time fails the post-processing`() {
        val service = FakePipelinesService(timedOut = true)
        val ex = assertFailsWith<BitbucketCloudPostProcessingFailureException> {
            run(postProcessing(service = service), sampleConfig)
        }
        assertEquals(RUN_URL, ex.link)
        assertEquals(
            "Bitbucket pipeline $RUN_URL did not complete in 100 seconds (last state: IN_PROGRESS).",
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
                "workspace" to "ws",
                "repository" to "repo",
                "pipeline" to "pipeline",
            ).asJson()
        )
        assertEquals("image", config.dockerImage)
        assertEquals("pipeline", config.pipeline)
    }

    @Test
    fun `Validation rejects a missing Bitbucket Cloud configuration`() {
        val ex = assertFailsWith<BitbucketCloudPostProcessingConfigException> {
            postProcessing(settings = emptySettings).parseAndValidate(
                mapOf("workspace" to "ws", "repository" to "repo", "pipeline" to "pipeline").asJson()
            )
        }
        assertEquals(
            "No Bitbucket Cloud configuration is defined for the Bitbucket Cloud post-processing, neither in the order nor in the settings.",
            ex.message
        )
    }

    @Test
    fun `Validation rejects an unknown Bitbucket Cloud configuration`() {
        val ex = assertFailsWith<BitbucketCloudPostProcessingConfigException> {
            postProcessing().parseAndValidate(mapOf("config" to "unknown").asJson())
        }
        assertEquals("Cannot find Bitbucket Cloud configuration with name: unknown", ex.message)
    }

    @Test
    fun `Validation rejects a missing workspace`() {
        val ex = assertFailsWith<BitbucketCloudPostProcessingConfigException> {
            postProcessing(settings = emptySettings).parseAndValidate(
                mapOf("config" to "other-config", "repository" to "repo", "pipeline" to "pipeline").asJson()
            )
        }
        assertEquals(
            "No workspace is defined for the Bitbucket Cloud post-processing, neither in the order nor in the settings.",
            ex.message
        )
    }

    @Test
    fun `Validation rejects a missing repository`() {
        val ex = assertFailsWith<BitbucketCloudPostProcessingConfigException> {
            postProcessing(settings = emptySettings).parseAndValidate(
                mapOf("config" to "other-config", "workspace" to "ws", "pipeline" to "pipeline").asJson()
            )
        }
        assertEquals(
            "No repository is defined for the Bitbucket Cloud post-processing, neither in the order nor in the settings.",
            ex.message
        )
    }

    @Test
    fun `Validation rejects a missing pipeline`() {
        val ex = assertFailsWith<BitbucketCloudPostProcessingConfigException> {
            postProcessing(settings = emptySettings).parseAndValidate(
                mapOf("config" to "other-config", "workspace" to "ws", "repository" to "repo").asJson()
            )
        }
        assertEquals(
            "No pipeline is defined for the Bitbucket Cloud post-processing, neither in the order nor in the settings.",
            ex.message
        )
    }

    @Test
    fun `Post-processing is not triggered when the configuration is incomplete`() {
        val service = FakePipelinesService()
        assertFailsWith<BitbucketCloudPostProcessingConfigException> {
            run(postProcessing(settings = emptySettings, service = service), sampleConfig)
        }
        assertNull(service.triggered)
    }

    companion object {
        private const val RUN_URL = "https://bitbucket.org/default-ws/default-repo/pipelines/results/12"
    }
}
