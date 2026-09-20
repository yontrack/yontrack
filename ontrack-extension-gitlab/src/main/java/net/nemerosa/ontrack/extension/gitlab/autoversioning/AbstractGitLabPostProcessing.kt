package net.nemerosa.ontrack.extension.gitlab.autoversioning

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessing
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.gitlab.GitLabExtensionFeature
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelinesService
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import java.time.Duration

/**
 * Runs the post-processing in a GitLab pipeline, and waits for its completion.
 *
 * The calls go through a [GitLabPipelinesService] - the seam the `gitlab-pipeline` notification channel already
 * uses - which the mock post-processing replaces.
 *
 * Unlike the channel there is no asynchronous mode: an auto-versioning order is only complete once the
 * post-processing has pushed its changes on the upgrade branch, so this always waits.
 *
 * Nothing of the [GitLabConfiguration] but its identity leaves this class: the token is attached to the
 * requests by the client, and never reaches a pipeline variable, a log, or the stored [PostProcessingInfo].
 */
abstract class AbstractGitLabPostProcessing(
    extensionFeature: GitLabExtensionFeature,
    private val cachedSettingsService: CachedSettingsService,
    private val gitLabConfigurationService: GitLabConfigurationService,
    private val gitLabPipelinesService: GitLabPipelinesService,
) : AbstractExtension(extensionFeature), PostProcessing<GitLabPostProcessingConfig> {

    override fun parseAndValidate(config: JsonNode?): GitLabPostProcessingConfig {
        val parsed = if (config != null && !config.isNull) {
            config.parse<GitLabPostProcessingConfig>()
        } else {
            GitLabPostProcessingConfig()
        }
        resolve(parsed, settings())
        return parsed
    }

    override fun postProcessing(
        config: GitLabPostProcessingConfig,
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

        val ref = render(
            config.ref?.takeIf { it.isNotBlank() }
                ?: settings.ref.takeIf { it.isNotBlank() }
                ?: GitLabPostProcessingSettings.DEFAULT_REF
        )

        val trigger = gitLabPipelinesService.trigger(
            configuration = target.configuration,
            project = target.project,
            ref = ref,
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
        val completion = gitLabPipelinesService.waitForCompletion(
            configuration = target.configuration,
            project = target.project,
            pipelineId = trigger.id,
            timeout = timeout,
            interval = Duration.ofSeconds(settings.retriesDelaySeconds.toLong()),
        )

        if (completion.timedOut) {
            throw GitLabPostProcessingFailureException(
                message = "GitLab pipeline ${trigger.url} did not complete in ${timeout.seconds} seconds (last status: ${completion.status.status}).",
                link = trigger.url,
            )
        } else if (!completion.successful) {
            throw GitLabPostProcessingFailureException(
                message = "GitLab pipeline ${trigger.url} completed with status ${completion.status.status}.",
                link = trigger.url,
            )
        }
    }

    private fun settings() =
        cachedSettingsService.getCachedSettings(GitLabPostProcessingSettings::class.java)

    private class Target(
        val configuration: GitLabConfiguration,
        val project: String,
    )

    private fun resolve(
        config: GitLabPostProcessingConfig,
        settings: GitLabPostProcessingSettings,
    ): Target {
        fun required(name: String, value: String?, default: String?): String =
            value?.takeIf { it.isNotBlank() }
                ?: default?.takeIf { it.isNotBlank() }
                ?: throw GitLabPostProcessingConfigException(
                    "No $name is defined for the GitLab post-processing, neither in the order nor in the settings."
                )

        val configName = required("GitLab configuration", config.config, settings.config)
        val configuration = gitLabConfigurationService.findConfiguration(configName)
            ?: throw GitLabPostProcessingConfigException("Cannot find GitLab configuration with name: $configName")
        return Target(
            configuration = configuration,
            project = required("project", config.project, settings.project),
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
