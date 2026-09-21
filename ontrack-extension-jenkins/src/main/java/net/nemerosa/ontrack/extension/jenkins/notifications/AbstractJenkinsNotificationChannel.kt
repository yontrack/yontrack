package net.nemerosa.ontrack.extension.jenkins.notifications

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfigurationService
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsBuild
import net.nemerosa.ontrack.extension.jenkins.client.JenkinsClient
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.patchEnum
import net.nemerosa.ontrack.json.patchInt
import net.nemerosa.ontrack.json.patchString
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.utils.patchList

abstract class AbstractJenkinsNotificationChannel(
    private val jenkinsConfigurationService: JenkinsConfigurationService,
    private val eventTemplatingService: EventTemplatingService,
) : AbstractNotificationChannel<JenkinsNotificationChannelConfig, JenkinsNotificationChannelOutput>(
    JenkinsNotificationChannelConfig::class
) {

    override fun validateParsedConfig(config: JenkinsNotificationChannelConfig) {
        if (config.config.isBlank()) {
            throw EventSubscriptionConfigException("Jenkins configuration name is required")
        } else {
            jenkinsConfigurationService.findConfiguration(config.config)
                ?: throw EventSubscriptionConfigException("Jenkins configuration ${config.config} could not be found")
        }
        if (config.job.isBlank()) {
            throw EventSubscriptionConfigException("Jenkins job is required")
        }
    }

    override fun mergeConfig(
        a: JenkinsNotificationChannelConfig,
        changes: JsonNode
    ) = JenkinsNotificationChannelConfig(
        config = patchString(changes, a::config),
        job = patchString(changes, a::job),
        parameters = patchList(changes, a::parameters) { it.name },
        callMode = patchEnum(changes, a::callMode),
        timeout = patchInt(changes, a::timeout),
    )

    override fun publish(
        recordId: String,
        config: JenkinsNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: JenkinsNotificationChannelOutput) -> JenkinsNotificationChannelOutput
    ): NotificationResult<JenkinsNotificationChannelOutput> {
        // Gets the Jenkins configuration
        val jenkinsConfig = jenkinsConfigurationService.findConfiguration(config.config)
            ?: return NotificationResult.invalidConfiguration("Jenkins configuration cannot be found: ${config.config}")
        // Gets the Jenkins client
        val jenkinsClient = createJenkinsClient(jenkinsConfig)
        // Getting the job parameters
        val job = eventTemplatingService.render(
            template = config.job,
            event = event,
            context = context,
            renderer = PlainEventRenderer.INSTANCE,
        )
        val parameters = config.parameters.associate {
            it.name to eventTemplatingService.render(
                template = it.value,
                event = event,
                context = context,
                renderer = PlainEventRenderer.INSTANCE
            )
        }
        // Filling the output
        val jobUrl = jenkinsClient.getJob(job).url
        var output = outputProgressCallback(
            JenkinsNotificationChannelOutput(
                jobUrl = jobUrl,
                buildUrl = null,
                parameters = parameters.map { (name, value) -> JenkinsNotificationChannelConfigParam(name, value) },
            )
        )
        // Running the job
        val (error, buildUrl) = when (config.callMode) {
            JenkinsNotificationChannelConfigCallMode.ASYNC -> launchAsync(jenkinsClient, job, parameters)
            JenkinsNotificationChannelConfigCallMode.SYNC -> launchSync(
                jenkinsClient,
                job,
                config.timeout,
                parameters
            ) { build ->
                output = outputProgressCallback(
                    output.withBuildUrl(build.url)
                )
            }
        }
        // Output
        output = outputProgressCallback(
            output.withBuildUrl(buildUrl)
        )
        // In case of error
        return if (error != null) {
            NotificationResult.error(
                message = error,
                output = output,
            )
        }
        // OK
        else {
            NotificationResult.ok(output = output)
        }
    }

    protected abstract fun createJenkinsClient(config: JenkinsConfiguration): JenkinsClient

    private fun launchSync(
        jenkinsClient: JenkinsClient,
        job: String,
        timeout: Int,
        parameters: Map<String, String>,
        buildFeedback: (build: JenkinsBuild) -> Unit,
    ): JobLaunchResult {
        val interval = 30 // seconds
        val retries = timeout / interval
        val build = jenkinsClient.runJob(
            job = job,
            parameters = parameters,
            retries = retries,
            retriesDelaySeconds = interval,
            buildFeedback = buildFeedback,
        )
        return if (build.successful) {
            JobLaunchResult(
                error = null,
                buildUrl = build.url,
            )
        } else {
            JobLaunchResult(
                error = "Jenkins build at ${build.url} was not reported successful.",
                buildUrl = build.url,
            )
        }
    }

    private fun launchAsync(
        jenkinsClient: JenkinsClient,
        job: String,
        parameters: Map<String, String>
    ): JobLaunchResult {
        val queueURI = jenkinsClient.fireAndForgetJob(
            job = job,
            parameters = parameters,
        )
        return if (queueURI != null) {
            JobLaunchResult(
                error = null,
                buildUrl = null,
            )
        } else {
            JobLaunchResult(
                error = "Could not find job at $job",
                buildUrl = null,
            )
        }
    }

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(
            JenkinsNotificationChannelConfig::config.name to text,
        ).asJson()

    override val enabled: Boolean = true

    data class JobLaunchResult(
        val error: String?,
        val buildUrl: String?,
    )

}