package net.nemerosa.ontrack.extension.jira.notifications

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.mergeList
import net.nemerosa.ontrack.extension.jira.JIRAConfiguration
import net.nemerosa.ontrack.extension.jira.JIRAConfigurationService
import net.nemerosa.ontrack.extension.jira.tx.JIRASessionFactory
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.*
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import org.springframework.stereotype.Component

@Component
@APIDescription("Creation of a Jira ticket")
@Documentation(JiraCreationNotificationChannelConfig::class)
@Documentation(JiraCreationNotificationChannelOutput::class, section = "output")
class JiraCreationNotificationChannel(
    private val jiraConfigurationService: JIRAConfigurationService,
    private val jiraSessionFactory: JIRASessionFactory,
    private val eventTemplatingService: EventTemplatingService,
    private val jiraNotificationEventRenderer: JiraNotificationEventRenderer,
) : AbstractNotificationChannel<JiraCreationNotificationChannelConfig, JiraCreationNotificationChannelOutput>(
    JiraCreationNotificationChannelConfig::class
) {

    override fun validateParsedConfig(config: JiraCreationNotificationChannelConfig) {
        if (config.configName.isBlank()) {
            throw EventSubscriptionConfigException("Jira config name is required")
        } else {
            jiraConfigurationService.findConfiguration(config.configName)
                ?: throw EventSubscriptionConfigException("Jira config configuration ${config.configName} does not exist")
        }
    }

    override fun mergeConfig(
        a: JiraCreationNotificationChannelConfig,
        changes: JsonNode
    ) = JiraCreationNotificationChannelConfig(
        configName = patchString(changes, a::configName),
        useExisting = patchBoolean(changes, a::useExisting),
        projectName = patchString(changes, a::projectName),
        issueType = patchString(changes, a::issueType),
        labels = patchStringList(changes, a::labels),
        fixVersion = patchNullableString(changes, a::fixVersion),
        assignee = patchNullableString(changes, a::assignee),
        titleTemplate = patchString(changes, a::titleTemplate),
        customFields = if (changes.has(JiraCreationNotificationChannelConfig::customFields.name)) {
            val customChanges = changes.path(JiraCreationNotificationChannelConfig::customFields.name)
                .values().map { it.parse<JiraCustomField>() }
            mergeList(a.customFields, customChanges, JiraCustomField::name) { e, _ -> e }
        } else {
            a.customFields
        }
    )

    override fun publish(
        recordId: String,
        config: JiraCreationNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: JiraCreationNotificationChannelOutput) -> JiraCreationNotificationChannelOutput,
    ): NotificationResult<JiraCreationNotificationChannelOutput> {
        // Getting the Jira configuration
        val jiraConfig: JIRAConfiguration = jiraConfigurationService.getConfiguration(config.configName)

        // Output
        var output = outputProgressCallback(JiraCreationNotificationChannelOutput())

        // Expanded labels
        val expandedLabels = config.labels.map {
            eventTemplatingService.renderEvent(
                event = event,
                context = context,
                template = it,
                renderer = PlainEventRenderer.INSTANCE,
            )
        }

        output = outputProgressCallback(output.withLabels(expandedLabels))

        // Gets the Jira client
        val jiraClient = jiraSessionFactory.create(jiraConfig).client

        // Checking for the existence of the ticket
        if (config.useExisting) {
            var jql = """project = "${config.projectName}" AND issuetype = "${config.issueType}""""
            expandedLabels.forEach { label ->
                jql += """ AND labels = "$label""""
            }
            output = outputProgressCallback(output.withJql(jql))
            val existingStub = jiraClient.searchIssueStubs(jiraConfig, jql).firstOrNull()
            if (existingStub != null) {
                return NotificationResult.ok(output.withStub(existingStub).withExisting(true))
            } else {
                output = outputProgressCallback(output.withExisting(false))
            }
        }

        // Title
        val title = eventTemplatingService.renderEvent(
            event = event,
            context = context,
            template = config.titleTemplate,
            renderer = PlainEventRenderer.INSTANCE
        )
        output = outputProgressCallback(output.withTitle(title))

        // Fix version
        val fixVersion = config.fixVersion
            ?.takeIf { it.isNotBlank() }
            ?.let {
                eventTemplatingService.renderEvent(
                    event = event,
                    context = context,
                    template = it,
                    renderer = PlainEventRenderer.INSTANCE
                )
            }
        output = outputProgressCallback(output.withFixVersion(fixVersion))

        // Custom fields
        val customFields = config.customFields.map { (name, value) ->
            JiraCustomField(name, value.transform { text ->
                eventTemplatingService.renderEvent(
                    event = event,
                    context = context,
                    template = text,
                    renderer = PlainEventRenderer.INSTANCE
                )
            })
        }
        output = outputProgressCallback(output.withCustomFields(customFields))

        // Body
        val body = eventTemplatingService.renderEvent(
            event = event,
            context = context,
            template = template,
            renderer = jiraNotificationEventRenderer,
        )
        output = outputProgressCallback(output.withBody(body))

        // Creates the issue
        val jiraIssueStub = jiraClient.createIssue(
            configuration = jiraConfig,
            project = config.projectName,
            issueType = config.issueType,
            labels = expandedLabels,
            fixVersion = fixVersion,
            assignee = config.assignee,
            title = title,
            customFields = customFields,
            body = body,
        )

        // OK
        return NotificationResult.ok(output.withStub(jiraIssueStub))
    }

    override fun toSearchCriteria(text: String): JsonNode =
        mapOf(
            JiraCreationNotificationChannelConfig::titleTemplate.name to text
        ).asJson()

    override val type: String = "jira-creation"

    override val displayName: String = "Jira ticket creation"

    override val enabled: Boolean = true

}