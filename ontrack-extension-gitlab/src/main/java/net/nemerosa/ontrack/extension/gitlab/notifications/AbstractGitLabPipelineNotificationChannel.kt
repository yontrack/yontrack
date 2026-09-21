package net.nemerosa.ontrack.extension.gitlab.notifications

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.gitlab.pipelines.GitLabPipelinesService
import net.nemerosa.ontrack.extension.gitlab.service.GitLabConfigurationService
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
import java.time.Duration

/**
 * Triggers a GitLab pipeline, and waits for it in `SYNC` mode.
 *
 * The actual calls go through a [GitLabPipelinesService], which the mock channel replaces.
 */
abstract class AbstractGitLabPipelineNotificationChannel(
    private val gitLabConfigurationService: GitLabConfigurationService,
    private val eventTemplatingService: EventTemplatingService,
    private val gitLabPipelinesService: GitLabPipelinesService,
) : AbstractNotificationChannel<GitLabPipelineNotificationChannelConfig, GitLabPipelineNotificationChannelOutput>(
    configClass = GitLabPipelineNotificationChannelConfig::class,
) {

    override val enabled: Boolean = true

    override fun validateParsedConfig(config: GitLabPipelineNotificationChannelConfig) {
        if (config.config.isBlank()) {
            throw EventSubscriptionConfigException("GitLab configuration name is required")
        } else {
            gitLabConfigurationService.findConfiguration(config.config)
                ?: throw EventSubscriptionConfigException("GitLab configuration ${config.config} could not be found")
        }
        if (config.project.isBlank()) {
            throw EventSubscriptionConfigException("GitLab project is required")
        }
        if (config.ref.isBlank()) {
            throw EventSubscriptionConfigException("GitLab pipeline ref is required")
        }
        if (config.variables.any { it.name.isBlank() }) {
            throw EventSubscriptionConfigException("GitLab pipeline variable names are required")
        }
    }

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(
            GitLabPipelineNotificationChannelConfig::config.name to text,
        ).asJson()

    override fun mergeConfig(
        a: GitLabPipelineNotificationChannelConfig,
        changes: JsonNode
    ) = GitLabPipelineNotificationChannelConfig(
        config = patchString(changes, a::config),
        project = patchString(changes, a::project),
        ref = patchString(changes, a::ref),
        variables = patchList(changes, a::variables) { it.name },
        callMode = patchEnum(changes, a::callMode),
        timeoutSeconds = patchInt(changes, a::timeoutSeconds),
    )

    override fun publish(
        recordId: String,
        config: GitLabPipelineNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: GitLabPipelineNotificationChannelOutput) -> GitLabPipelineNotificationChannelOutput
    ): NotificationResult<GitLabPipelineNotificationChannelOutput> {

        val configuration = gitLabConfigurationService.findConfiguration(config.config)
            ?: return NotificationResult.invalidConfiguration("GitLab configuration cannot be found: ${config.config}")

        fun render(value: String) = eventTemplatingService.render(
            template = value,
            event = event,
            context = context,
            renderer = PlainEventRenderer.INSTANCE,
        )

        val project = render(config.project)
        val ref = render(config.ref)
        val variables = config.variables.map {
            GitLabPipelineNotificationChannelConfigVariable(name = it.name, value = render(it.value))
        }

        var output = outputProgressCallback(
            GitLabPipelineNotificationChannelOutput(
                project = project,
                ref = ref,
                variables = variables,
            )
        )

        // Triggering
        val trigger = try {
            gitLabPipelinesService.trigger(
                configuration = configuration,
                project = project,
                ref = ref,
                variables = variables.associate { it.name to it.value },
            )
        } catch (any: Exception) {
            return NotificationResult.error(
                message = "GitLab pipeline could not be triggered: ${any.message}",
                output = output,
            )
        }
        output = outputProgressCallback(
            output.copy(
                id = trigger.id,
                iid = trigger.iid,
                url = trigger.url,
                status = trigger.status,
            )
        )

        if (config.callMode == GitLabPipelineNotificationChannelConfigCallMode.ASYNC) {
            return NotificationResult.ok(output)
        }

        // Waiting
        val completion = try {
            gitLabPipelinesService.waitForCompletion(
                configuration = configuration,
                project = project,
                pipelineId = trigger.id,
                timeout = Duration.ofSeconds(config.timeoutSeconds.toLong()),
            ) { status ->
                output = outputProgressCallback(output.copy(status = status.status))
            }
        } catch (any: Exception) {
            return NotificationResult.error(
                message = "GitLab pipeline ${trigger.url} could not be followed: ${any.message}",
                output = output,
            )
        }
        output = outputProgressCallback(output.copy(status = completion.status.status))

        return when {
            completion.timedOut -> NotificationResult.error(
                message = "GitLab pipeline ${trigger.url} did not complete in ${config.timeoutSeconds} seconds (last status: ${completion.status.status}).",
                output = output,
            )

            !completion.successful -> NotificationResult.error(
                message = "GitLab pipeline ${trigger.url} completed with status ${completion.status.status}.",
                output = output,
            )

            else -> NotificationResult.ok(output)
        }
    }
}
