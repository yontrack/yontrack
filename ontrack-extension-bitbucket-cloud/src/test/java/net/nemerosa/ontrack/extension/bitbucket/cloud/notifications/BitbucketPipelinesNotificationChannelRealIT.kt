package net.nemerosa.ontrack.extension.bitbucket.cloud.notifications

import net.nemerosa.ontrack.extension.bitbucket.cloud.AbstractBitbucketCloudTestSupport
import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudTestEnv
import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudTestFixture
import net.nemerosa.ontrack.extension.bitbucket.cloud.TestOnBitbucketCloudPipelines
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigReal
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestEnv
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventFactory
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The `bitbucket-pipelines` channel against the custom pipelines of the fixture repository.
 *
 * Every test runs a real pipeline and consumes build minutes, which is why only
 * `.github/workflows/bitbucket-real.yml` runs them - see the module's README (#1761).
 * Three pipelines in total, each of a few seconds of build time.
 */
@AsAdminTest
class BitbucketPipelinesNotificationChannelRealIT : AbstractBitbucketCloudTestSupport() {

    @Autowired
    private lateinit var channel: BitbucketPipelinesNotificationChannel

    @Autowired
    private lateinit var eventFactory: EventFactory

    @Autowired
    private lateinit var bitbucketCloudClientFactory: BitbucketCloudClientFactory

    private val env: BitbucketCloudTestEnv get() = bitbucketCloudTestEnv

    @TestOnBitbucketCloudPipelines
    fun `Async pipeline triggered`() {
        val (config, result) = publish(
            pipeline = BitbucketCloudTestFixture.PIPELINE_ECHO,
            variables = mapOf("MESSAGE" to "Async \${project}"),
            callMode = BitbucketPipelinesNotificationChannelConfigCallMode.ASYNC,
        )
        assertEquals(NotificationResultType.OK, result.type, result.message)
        assertNotNull(result.output) { output ->
            assertNotNull(output.uuid)
            assertNotNull(output.buildNumber)
            assertEquals(
                "https://bitbucket.org/${env.workspace}/${env.repository}/pipelines/results/${output.buildNumber}",
                output.url
            )
            assertEquals(null, output.state)
            reportBuildMinutes(config, output)
        }
    }

    @TestOnBitbucketCloudPipelines
    fun `Sync pipeline successful`() {
        val (config, result) = publish(
            pipeline = BitbucketCloudTestFixture.PIPELINE_ECHO,
            variables = mapOf("MESSAGE" to "Sync \${project}"),
            callMode = BitbucketPipelinesNotificationChannelConfigCallMode.SYNC,
        )
        assertEquals(NotificationResultType.OK, result.type, result.message)
        assertEquals("SUCCESSFUL", result.output?.state)
        result.output?.let { reportBuildMinutes(config, it) }
    }

    @TestOnBitbucketCloudPipelines
    fun `Sync pipeline failing`() {
        val (config, result) = publish(
            pipeline = BitbucketCloudTestFixture.PIPELINE_FAIL,
            variables = mapOf("FAIL" to "true"),
            callMode = BitbucketPipelinesNotificationChannelConfigCallMode.SYNC,
        )
        assertEquals(NotificationResultType.ERROR, result.type, result.message)
        assertEquals("FAILED", result.output?.state)
        result.output?.let { reportBuildMinutes(config, it) }
    }

    private fun publish(
        pipeline: String,
        variables: Map<String, String>,
        callMode: BitbucketPipelinesNotificationChannelConfigCallMode,
    ): Pair<BitbucketCloudConfiguration, NotificationResult<BitbucketPipelinesNotificationChannelOutput>> {
        val config = bitbucketCloudTestConfigReal()
        withDisabledConfigurationTest {
            bitbucketCloudConfigurationService.newConfiguration(config)
        }
        var result: NotificationResult<BitbucketPipelinesNotificationChannelOutput>? = null
        project {
            result = channel.publish(
                recordId = "1",
                config = BitbucketPipelinesNotificationChannelConfig(
                    config = config.name,
                    workspace = env.workspace,
                    repository = env.repository,
                    branch = BitbucketCloudTestFixture.MAIN_BRANCH,
                    pipeline = pipeline,
                    variables = variables.map { (name, value) ->
                        BitbucketPipelinesNotificationChannelConfigVariable(name, value)
                    },
                    callMode = callMode,
                    timeoutSeconds = SYNC_TIMEOUT_SECONDS,
                ),
                event = eventFactory.newProject(this),
                context = emptyMap(),
                template = null,
            ) { it }
        }
        return config to (result ?: error("No result"))
    }

    /**
     * Logs what the pipeline consumed - a plain read, no pipeline is started.
     */
    private fun reportBuildMinutes(config: BitbucketCloudConfiguration, output: BitbucketPipelinesNotificationChannelOutput) {
        val uuid = output.uuid ?: return
        val run = bitbucketCloudClientFactory.getBitbucketCloudClient(config)
            .getPipeline(env.workspace, env.repository, uuid)
        println(
            "[bitbucket-pipelines] #${run.buildNumber} state=${run.stateName} " +
                    "duration_in_seconds=${run.durationInSeconds} build_seconds_used=${run.buildSecondsUsed}"
        )
    }

    companion object {
        const val SYNC_TIMEOUT_SECONDS = 300
    }
}
