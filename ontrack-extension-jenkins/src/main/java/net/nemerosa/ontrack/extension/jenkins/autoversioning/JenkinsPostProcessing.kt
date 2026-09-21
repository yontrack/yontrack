package net.nemerosa.ontrack.extension.jenkins.autoversioning

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessing
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingInfo
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingMissingConfigException
import net.nemerosa.ontrack.extension.av.processing.AutoVersioningTemplateRenderer
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfigurationService
import net.nemerosa.ontrack.extension.jenkins.JenkinsExtensionFeature
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsClientFactory
import net.nemerosa.ontrack.extension.scm.service.SCM
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.springframework.stereotype.Component

/**
 * Upgrade post-processing based on a generic job in Jenkins.
 */
@Component
class JenkinsPostProcessing(
    extensionFeature: JenkinsExtensionFeature,
    private val cachedSettingsService: CachedSettingsService,
    private val jenkinsConfigurationService: JenkinsConfigurationService,
    private val jenkinsClientFactory: JenkinsClientFactory,
) : AbstractExtension(extensionFeature), PostProcessing<JenkinsPostProcessingConfig> {

    override val id: String = "jenkins"

    override val name: String = "Jenkins job post processing"

    override fun parseAndValidate(config: JsonNode?): JenkinsPostProcessingConfig {
        return if (config != null && !config.isNull) {
            JenkinsPostProcessingConfig.parseJson(config)
        } else {
            throw PostProcessingMissingConfigException()
        }
    }

    override fun postProcessing(
        config: JenkinsPostProcessingConfig,
        autoVersioningOrder: AutoVersioningOrder,
        repositoryURI: String,
        repository: String,
        upgradeBranch: String,
        scm: SCM,
        avTemplateRenderer: AutoVersioningTemplateRenderer,
        onPostProcessingInfo: (info: PostProcessingInfo) -> Unit,
    ) {
        // Gets the global settings
        val settings: JenkinsPostProcessingSettings =
            cachedSettingsService.getCachedSettings(JenkinsPostProcessingSettings::class.java)
        // Configuration
        val jenkinsConfigName = config.config ?: settings.config
        val jenkinsJobPathTemplate = config.job ?: settings.job
        val jenkinsJobPath = avTemplateRenderer.render(jenkinsJobPathTemplate, PlainEventRenderer.INSTANCE)
        // Checks the configuration
        if (jenkinsConfigName.isBlank() || jenkinsJobPath.isBlank()) {
            throw JenkinsPostProcessingSettingsNotFoundException()
        }
        // Gets the Jenkins configuration by name
        val jenkinsConfig: JenkinsConfiguration = jenkinsConfigurationService.getConfiguration(jenkinsConfigName)

        // Gets a Jenkins client for this configuration
        val jenkinsClient = jenkinsClientFactory.getClient(jenkinsConfig)

        // Parameters to send to the job
        val parameters = mutableMapOf(
            "DOCKER_IMAGE" to avTemplateRenderer.render(config.dockerImage, PlainEventRenderer.INSTANCE),
            "DOCKER_COMMAND" to avTemplateRenderer.render(config.dockerCommand, PlainEventRenderer.INSTANCE),
            "COMMIT_MESSAGE" to (config.commitMessage ?: autoVersioningOrder.defaultCommitMessage),
            "REPOSITORY_URI" to repositoryURI,
            "UPGRADE_BRANCH" to upgradeBranch,
            "VERSION" to autoVersioningOrder.targetVersion,
            "CREDENTIALS" to (config.credentials?.renderParameter() ?: ""),
        )

        // Extra parameters
        parameters.putAll(
            config.parameters.associate {
                it.name to avTemplateRenderer.render(it.value, PlainEventRenderer.INSTANCE)
            }
        )

        // Launches the job and waits for its completion
        val jenkinsBuild = jenkinsClient.runJob(
            jenkinsJobPath,
            parameters,
            settings.retries,
            settings.retriesDelaySeconds
        ) { build ->
            onPostProcessingInfo(
                PostProcessingInfo(
                    data = mapOf(
                        "url" to build.url,
                    )
                )
            )
        }

        // Check for success
        if (!jenkinsBuild.successful) {
            throw JenkinsPostProcessingJobFailureException(
                jenkins = jenkinsConfig.url,
                job = jenkinsJobPath,
                build = jenkinsBuild.id,
                buildUrl = jenkinsBuild.url,
                result = jenkinsBuild.result
            )
        }
    }

    private val AutoVersioningOrder.defaultCommitMessage: String
        get() = "Post processing for version change in ${defaultPath.paths.first()} for $targetVersion"

}
