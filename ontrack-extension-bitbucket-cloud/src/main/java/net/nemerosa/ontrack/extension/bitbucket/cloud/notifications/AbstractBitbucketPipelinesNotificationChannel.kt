package net.nemerosa.ontrack.extension.bitbucket.cloud.notifications

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfigurationService
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.BitbucketPipelinesService
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.patchEnum
import net.nemerosa.ontrack.json.patchInt
import net.nemerosa.ontrack.json.patchNullableString
import net.nemerosa.ontrack.json.patchString
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.utils.patchList
import java.time.Duration

/**
 * Triggers a Bitbucket pipeline, and waits for it in `SYNC` mode.
 *
 * The actual calls go through a [BitbucketPipelinesService], which the mock channel replaces.
 */
abstract class AbstractBitbucketPipelinesNotificationChannel(
    private val bitbucketCloudConfigurationService: BitbucketCloudConfigurationService,
    private val eventTemplatingService: EventTemplatingService,
    private val bitbucketPipelinesService: BitbucketPipelinesService,
) : AbstractNotificationChannel<BitbucketPipelinesNotificationChannelConfig, BitbucketPipelinesNotificationChannelOutput>(
    configClass = BitbucketPipelinesNotificationChannelConfig::class,
) {

    override val enabled: Boolean = true

    override fun validateParsedConfig(config: BitbucketPipelinesNotificationChannelConfig) {
        if (config.config.isBlank()) {
            throw EventSubscriptionConfigException("Bitbucket Cloud configuration name is required")
        } else {
            bitbucketCloudConfigurationService.findConfiguration(config.config)
                ?: throw EventSubscriptionConfigException("Bitbucket Cloud configuration ${config.config} could not be found")
        }
        if (config.workspace.isBlank()) {
            throw EventSubscriptionConfigException("Bitbucket pipeline workspace is required")
        }
        if (config.repository.isBlank()) {
            throw EventSubscriptionConfigException("Bitbucket pipeline repository is required")
        }
        if (config.branch.isBlank()) {
            throw EventSubscriptionConfigException("Bitbucket pipeline branch is required")
        }
        if (config.variables.any { it.name.isBlank() }) {
            throw EventSubscriptionConfigException("Bitbucket pipeline variable names are required")
        }
    }

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(
            BitbucketPipelinesNotificationChannelConfig::config.name to text,
        ).asJson()

    override fun mergeConfig(
        a: BitbucketPipelinesNotificationChannelConfig,
        changes: JsonNode
    ) = BitbucketPipelinesNotificationChannelConfig(
        config = patchString(changes, a::config),
        workspace = patchString(changes, a::workspace),
        repository = patchString(changes, a::repository),
        branch = patchString(changes, a::branch),
        pipeline = patchNullableString(changes, a::pipeline),
        variables = patchList(changes, a::variables) { it.name },
        callMode = patchEnum(changes, a::callMode),
        timeoutSeconds = patchInt(changes, a::timeoutSeconds),
    )

    override fun publish(
        recordId: String,
        config: BitbucketPipelinesNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: BitbucketPipelinesNotificationChannelOutput) -> BitbucketPipelinesNotificationChannelOutput
    ): NotificationResult<BitbucketPipelinesNotificationChannelOutput> {

        val configuration = bitbucketCloudConfigurationService.findConfiguration(config.config)
            ?: return NotificationResult.invalidConfiguration("Bitbucket Cloud configuration cannot be found: ${config.config}")

        fun render(value: String) = eventTemplatingService.render(
            template = value,
            event = event,
            context = context,
            renderer = PlainEventRenderer.INSTANCE,
        )

        val workspace = render(config.workspace)
        val repository = render(config.repository)
        val branch = render(config.branch)
        val pipeline = config.pipeline?.takeIf { it.isNotBlank() }?.let { render(it) }
        val variables = config.variables.map {
            BitbucketPipelinesNotificationChannelConfigVariable(name = it.name, value = render(it.value))
        }

        var output = outputProgressCallback(
            BitbucketPipelinesNotificationChannelOutput(
                workspace = workspace,
                repository = repository,
                branch = branch,
                pipeline = pipeline,
                variables = variables,
            )
        )

        // Triggering
        val trigger = try {
            bitbucketPipelinesService.trigger(
                configuration = configuration,
                workspace = workspace,
                repository = repository,
                branch = branch,
                pipeline = pipeline,
                variables = variables.associate { it.name to it.value },
            )
        } catch (any: Exception) {
            return NotificationResult.error(
                message = "Bitbucket pipeline could not be triggered: ${any.message}",
                output = output,
            )
        }
        output = outputProgressCallback(
            output.copy(
                uuid = trigger.uuid,
                buildNumber = trigger.buildNumber,
                url = trigger.url,
            )
        )

        if (config.callMode == BitbucketPipelinesNotificationChannelConfigCallMode.ASYNC) {
            return NotificationResult.ok(output)
        }

        // Waiting
        val completion = try {
            bitbucketPipelinesService.waitForCompletion(
                configuration = configuration,
                workspace = workspace,
                repository = repository,
                uuid = trigger.uuid,
                timeout = Duration.ofSeconds(config.timeoutSeconds.toLong()),
            ) { status ->
                output = outputProgressCallback(output.copy(state = status.state))
            }
        } catch (any: Exception) {
            return NotificationResult.error(
                message = "Bitbucket pipeline ${trigger.url} could not be followed: ${any.message}",
                output = output,
            )
        }
        output = outputProgressCallback(output.copy(state = completion.status.state))

        return when {
            completion.timedOut -> NotificationResult.error(
                message = "Bitbucket pipeline ${trigger.url} did not complete in ${config.timeoutSeconds} seconds (last state: ${completion.status.state}).",
                output = output,
            )

            !completion.successful -> NotificationResult.error(
                message = "Bitbucket pipeline ${trigger.url} completed with state ${completion.status.state}.",
                output = output,
            )

            else -> NotificationResult.ok(output)
        }
    }
}
