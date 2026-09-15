package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.bitbucket.cloud.AbstractBitbucketCloudTestSupport
import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudExtensionFeature
import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudTestEnv
import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudTestFixture
import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudTestNames
import net.nemerosa.ontrack.extension.bitbucket.cloud.TestOnBitbucketCloudPipelines
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestConfigReal
import net.nemerosa.ontrack.extension.bitbucket.cloud.bitbucketCloudTestEnv
import net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClientFactory
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelinesService
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventRenderer
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `bitbucket-cloud` post-processing against the `yontrack-auto-versioning` custom pipeline of the fixture
 * repository, which commits the version to `post-processing.txt` on the upgrade branch.
 *
 * Runs one real pipeline and consumes build minutes, which is why it only runs in the release workflow.
 */
@AsAdminTest
class BitbucketCloudPostProcessingRealIT : AbstractBitbucketCloudTestSupport() {

    @Autowired
    private lateinit var extensionFeature: BitbucketCloudExtensionFeature

    @Autowired
    private lateinit var bitbucketPipelinesService: BitbucketPipelinesService

    @Autowired
    private lateinit var bitbucketCloudClientFactory: BitbucketCloudClientFactory

    private val names = BitbucketCloudTestNames()

    private val env: BitbucketCloudTestEnv get() = bitbucketCloudTestEnv

    @TestOnBitbucketCloudPipelines
    fun `Post-processing pipeline commits on the upgrade branch`() {
        val config = bitbucketCloudTestConfigReal()
        withDisabledConfigurationTest {
            bitbucketCloudConfigurationService.newConfiguration(config)
        }
        val client = bitbucketCloudClientFactory.getBitbucketCloudClient(config)
        val upgradeBranch = names.branch("av-post-processing")
        val version = "2.0.${System.currentTimeMillis() % 100_000}"

        // Capturing the UUID of the pipeline to report its build minutes
        var uuid: String? = null
        val capturingService = object : BitbucketPipelinesService by bitbucketPipelinesService {
            override fun trigger(
                configuration: net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration,
                workspace: String,
                repository: String,
                branch: String,
                pipeline: String?,
                variables: Map<String, String>
            ) = bitbucketPipelinesService.trigger(configuration, workspace, repository, branch, pipeline, variables)
                .also { uuid = it.uuid }
        }
        val postProcessing = BitbucketCloudPostProcessing(
            extensionFeature = extensionFeature,
            cachedSettingsService = cachedSettingsService,
            bitbucketCloudConfigurationService = bitbucketCloudConfigurationService,
            bitbucketPipelinesService = capturingService,
        )

        client.createBranch(env.workspace, env.repository, BitbucketCloudTestFixture.MAIN_BRANCH, upgradeBranch)
        try {
            val order = mockk<AutoVersioningOrder>()
            every { order.targetVersion } returns version
            val infos = mutableListOf<PostProcessingInfo>()

            try {
                postProcessing.postProcessing(
                    config = BitbucketCloudPostProcessingConfig(
                        dockerImage = "alpine",
                        dockerCommand = "true",
                        commitMessage = "Post-processing for $version",
                        config = config.name,
                        workspace = env.workspace,
                        repository = env.repository,
                        pipeline = BitbucketCloudTestFixture.PIPELINE_AUTO_VERSIONING,
                        branch = BitbucketCloudTestFixture.MAIN_BRANCH,
                    ),
                    autoVersioningOrder = order,
                    repositoryURI = "https://bitbucket.org/${env.workspace}/${env.repository}.git",
                    repository = "${env.workspace}/${env.repository}",
                    upgradeBranch = upgradeBranch,
                    scm = mockk(),
                    avTemplateRenderer = object : AutoVersioningTemplateRenderer {
                        override fun render(template: String, renderer: EventRenderer): String = template
                    },
                ) { infos += it }
            } finally {
                uuid?.let { reportBuildMinutes(client, it) }
            }

            val url = infos.single().data["url"] ?: error("No URL")
            assertTrue(url.startsWith("https://bitbucket.org/${env.workspace}/${env.repository}/pipelines/results/"), url)

            val content = client.download(env.workspace, env.repository, upgradeBranch, "post-processing.txt")
                ?.decodeToString()?.trim()
            assertEquals(version, content, "Version committed by the pipeline on the upgrade branch")
        } finally {
            client.deleteBranch(env.workspace, env.repository, upgradeBranch)
        }
    }

    /**
     * Logs what the pipeline consumed - a plain read, no pipeline is started.
     */
    private fun reportBuildMinutes(
        client: net.nemerosa.ontrack.extension.bitbucket.cloud.client.BitbucketCloudClient,
        uuid: String,
    ) {
        val run = client.getPipeline(env.workspace, env.repository, uuid)
        println(
            "[bitbucket-cloud-post-processing] #${run.buildNumber} state=${run.stateName} " +
                    "duration_in_seconds=${run.durationInSeconds} build_seconds_used=${run.buildSecondsUsed}"
        )
    }
}
