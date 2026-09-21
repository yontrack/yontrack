package net.nemerosa.ontrack.extension.bitbucket.cloud.autoversioning

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessing
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.bitbucket.cloud.BitbucketCloudExtensionFeature
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelinesService
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import java.time.Duration

/**
 * Runs the post-processing in a Bitbucket pipeline, and waits for its completion.
 *
 * The calls go through a [BitbucketPipelinesService], which the mock post-processing replaces.
 */
abstract class AbstractBitbucketCloudPostProcessing(
    extensionFeature: BitbucketCloudExtensionFeature,
    private val cachedSettingsService: CachedSettingsService,
    private val bitbucketCloudConfigurationService: BitbucketCloudConfigurationService,
    private val bitbucketPipelinesService: BitbucketPipelinesService,
) : AbstractExtension(extensionFeature), PostProcessing<BitbucketCloudPostProcessingConfig> {

    override fun parseAndValidate(config: JsonNode?): BitbucketCloudPostProcessingConfig {
        val parsed = if (config != null && !config.isNull) {
            config.parse<BitbucketCloudPostProcessingConfig>()
        } else {
            BitbucketCloudPostProcessingConfig()
        }
        resolve(parsed, settings())
        return parsed
    }

    override fun postProcessing(
        config: BitbucketCloudPostProcessingConfig,
        autoVersioningOrder: AutoVersioningOrder,
        repositoryURI: String,
        repository: String,
        upgradeBranch: String,
        scm: SCM,
        avTemplateRenderer: AutoVersioningTemplateRenderer,
        onPostProcessingInfo: (info: PostProcessingInfo) -> Unit,
    ) {
        val settings = settings()
        val target = resolve(config, settings)

        fun render(value: String?) = value?.let { avTemplateRenderer.render(it, PlainEventRenderer.INSTANCE) } ?: ""

        val branch = render(
            config.branch?.takeIf { it.isNotBlank() }
                ?: settings.branch.takeIf { it.isNotBlank() }
                ?: BitbucketCloudPostProcessingSettings.DEFAULT_BRANCH
        )

        val trigger = bitbucketPipelinesService.trigger(
            configuration = target.configuration,
            workspace = target.workspace,
            repository = target.repository,
            branch = branch,
            pipeline = target.pipeline,
            variables = mapOf(
                VAR_REPOSITORY to repositoryURI,
                VAR_UPGRADE_BRANCH to upgradeBranch,
                VAR_DOCKER_IMAGE to render(config.dockerImage),
                VAR_DOCKER_COMMAND to render(config.dockerCommand),
                VAR_COMMIT_MESSAGE to render(config.commitMessage),
                VAR_VERSION to autoVersioningOrder.targetVersion,
            ),
        )

        onPostProcessingInfo(
            PostProcessingInfo(
                data = mapOf("url" to trigger.url)
            )
        )

        val timeout = Duration.ofSeconds(settings.retries.toLong() * settings.retriesDelaySeconds)
        val completion = bitbucketPipelinesService.waitForCompletion(
            configuration = target.configuration,
            workspace = target.workspace,
            repository = target.repository,
            uuid = trigger.uuid,
            timeout = timeout,
            interval = Duration.ofSeconds(settings.retriesDelaySeconds.toLong()),
        )

        if (completion.timedOut) {
            throw BitbucketCloudPostProcessingFailureException(
                message = "Bitbucket pipeline ${trigger.url} did not complete in ${timeout.seconds} seconds (last state: ${completion.status.state}).",
                link = trigger.url,
            )
        } else if (!completion.successful) {
            throw BitbucketCloudPostProcessingFailureException(
                message = "Bitbucket pipeline ${trigger.url} completed with state ${completion.status.state}.",
                link = trigger.url,
            )
        }
    }

    private fun settings() =
        cachedSettingsService.getCachedSettings(BitbucketCloudPostProcessingSettings::class.java)

    private class Target(
        val configuration: BitbucketCloudConfiguration,
        val workspace: String,
        val repository: String,
        val pipeline: String,
    )

    private fun resolve(
        config: BitbucketCloudPostProcessingConfig,
        settings: BitbucketCloudPostProcessingSettings,
    ): Target {
        fun required(name: String, value: String?, default: String?): String =
            value?.takeIf { it.isNotBlank() }
                ?: default?.takeIf { it.isNotBlank() }
                ?: throw BitbucketCloudPostProcessingConfigException(
                    "No $name is defined for the Bitbucket Cloud post-processing, neither in the order nor in the settings."
                )

        val configName = required("Bitbucket Cloud configuration", config.config, settings.config)
        val configuration = bitbucketCloudConfigurationService.findConfiguration(configName)
            ?: throw BitbucketCloudPostProcessingConfigException("Cannot find Bitbucket Cloud configuration with name: $configName")
        return Target(
            configuration = configuration,
            workspace = required("workspace", config.workspace, settings.workspace),
            repository = required("repository", config.repository, settings.repository),
            pipeline = required("pipeline", config.pipeline, settings.pipeline),
        )
    }

    companion object {
        const val VAR_REPOSITORY = "REPOSITORY"
        const val VAR_UPGRADE_BRANCH = "UPGRADE_BRANCH"
        const val VAR_DOCKER_IMAGE = "DOCKER_IMAGE"
        const val VAR_DOCKER_COMMAND = "DOCKER_COMMAND"
        const val VAR_COMMIT_MESSAGE = "COMMIT_MESSAGE"
        const val VAR_VERSION = "VERSION"
    }
}
