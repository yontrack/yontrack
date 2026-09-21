package net.nemerosa.ontrack.extension.github.autoversioning

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessing
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingMissingConfigException
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.github.GitHubExtensionFeature
import net.nemerosa.ontrack.extension.github.client.OntrackGitHubClientFactory
import net.nemerosa.ontrack.extension.github.model.GitHubEngineConfiguration
import net.nemerosa.ontrack.extension.github.service.GitHubConfigurationService
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.springframework.stereotype.Component

@Component
class GitHubPostProcessing(
    extensionFeature: GitHubExtensionFeature,
    private val cachedSettingsService: CachedSettingsService,
    private val gitHubConfigurationService: GitHubConfigurationService,
    private val ontrackGitHubClientFactory: OntrackGitHubClientFactory,
) : AbstractExtension(extensionFeature), PostProcessing<GitHubPostProcessingConfig> {

    override val id: String = "github"

    override val name: String = "GitHub Actions workflow post processing"

    override fun parseAndValidate(config: JsonNode?): GitHubPostProcessingConfig =
        if (config != null && !config.isNull) {
            // Parsing
            config.parse()
        } else {
            throw PostProcessingMissingConfigException()
        }

    override fun postProcessing(
        config: GitHubPostProcessingConfig,
        autoVersioningOrder: AutoVersioningOrder,
        repositoryURI: String,
        repository: String,
        upgradeBranch: String,
        scm: SCM,
        avTemplateRenderer: AutoVersioningTemplateRenderer,
        onPostProcessingInfo: (info: PostProcessingInfo) -> Unit,
    ) {
        // Gets the settings
        val settings = cachedSettingsService.getCachedSettings(GitHubPostProcessingSettings::class.java)

        // Gets the name of the GitHub configuration
        val ghConfigName = config.config
            ?: settings.config?.takeIf { it.isNotBlank() }
            ?: throw GitHubPostProcessingConfigException("Default GitHub configuration is not defined in the settings.")

        // Loading the configuration
        val ghConfig = gitHubConfigurationService.findConfiguration(ghConfigName)
            ?: throw GitHubPostProcessingConfigException("Cannot find GitHub configuration with name: $ghConfigName")

        // Repository
        val ppRepository = config.repository?.takeIf { it.isNotBlank() }
            ?: settings.repository
            ?: throw GitHubPostProcessingConfigException("Cannot find any repository to launch the GitHub auto-versioning post-processing")

        // Workflow
        val workflow = config.workflow?.takeIf { it.isNotBlank() }
            ?: settings.workflow
            ?: throw GitHubPostProcessingConfigException("Cannot find any workflow to launch the GitHub auto-versioning post-processing")

        // Branch
        val branch = config.branch?.takeIf { it.isNotBlank() }
            ?: settings.branch
                .let { avTemplateRenderer.render(it, PlainEventRenderer.INSTANCE) }

        // Launching the post-processing
        runPostProcessing(
            ghConfig = ghConfig,
            config = config,
            repository = ppRepository,
            workflow = workflow,
            branch = branch,
            inputs = mapOf(
                "repository" to repository,
                "upgrade_branch" to upgradeBranch,
                "docker_image" to avTemplateRenderer.render(config.dockerImage, PlainEventRenderer.INSTANCE),
                "docker_command" to avTemplateRenderer.render(config.dockerCommand, PlainEventRenderer.INSTANCE),
                "commit_message" to avTemplateRenderer.render(config.commitMessage, PlainEventRenderer.INSTANCE),
                "version" to autoVersioningOrder.targetVersion,
            ),
            settings = settings,
            avTemplateRenderer = avTemplateRenderer,
            onPostProcessingInfo = onPostProcessingInfo,
        )

    }

    private fun runPostProcessing(
        ghConfig: GitHubEngineConfiguration,
        config: GitHubPostProcessingConfig,
        repository: String,
        workflow: String,
        branch: String,
        inputs: Map<String, String>,
        settings: GitHubPostProcessingSettings,
        avTemplateRenderer: AutoVersioningTemplateRenderer,
        onPostProcessingInfo: (info: PostProcessingInfo) -> Unit,
    ) {
        // Getting the GH client
        val client = ontrackGitHubClientFactory.create(ghConfig)
        // Preparation of the parameters
        val parameters = inputs.toMutableMap()
        parameters.putAll(
            config.parameters.associate {
                it.name to avTemplateRenderer.render(it.value, PlainEventRenderer.INSTANCE)
            }
        )
        // Launches the workflow run
        val runId = client.launchWorkflowRun(
            repository = repository,
            workflow = workflow,
            branch = branch,
            inputs = parameters.toMap(),
            retries = settings.retries,
            retriesDelaySeconds = settings.retriesDelaySeconds,
        ).id
        // Sending back the URL of the workflow run
        val url = "${ghConfig.url}/${repository}/actions/runs/${runId}"
        onPostProcessingInfo(
            PostProcessingInfo(
                data = mapOf(
                    "url" to url
                )
            )
        )
        // Waiting until the workflow run completes
        client.waitUntilWorkflowRun(
            repository = repository,
            runId = runId,
        )
    }

}