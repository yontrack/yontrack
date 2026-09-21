package net.nemerosa.ontrack.extension.jira.servicedesk

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.jira.JIRAConfiguration
import net.nemerosa.ontrack.extension.jira.JIRAConfigurationService
import net.nemerosa.ontrack.extension.jira.notifications.JiraCustomField
import net.nemerosa.ontrack.extension.jira.notifications.JiraNotificationEventRenderer
import net.nemerosa.ontrack.extension.jira.tx.JIRASessionFactory
import net.nemerosa.ontrack.extension.notifications.channels.AbstractNotificationChannel
import net.nemerosa.ontrack.extension.notifications.channels.NoTemplate
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResult
import net.nemerosa.ontrack.extension.notifications.subscriptions.EventSubscriptionConfigException
import net.nemerosa.ontrack.json.*
import net.nemerosa.ontrack.model.docs.Documentation
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.utils.patchList
import org.springframework.stereotype.Component

@Component
@APIDescription("This channel is used to create a Jira Service Desk ticket.")
@Documentation(JiraServiceDeskNotificationChannelConfig::class)
@Documentation(JiraServiceDeskNotificationChannelOutput::class, section = "output")
@NoTemplate
class JiraServiceDeskNotificationChannel(
    private val jiraConfigurationService: JIRAConfigurationService,
    private val jiraSessionFactory: JIRASessionFactory,
    private val eventTemplatingService: EventTemplatingService,
    private val jiraNotificationEventRenderer: JiraNotificationEventRenderer,
) : AbstractNotificationChannel<JiraServiceDeskNotificationChannelConfig, JiraServiceDeskNotificationChannelOutput>(
    JiraServiceDeskNotificationChannelConfig::class
) {
    override fun validateParsedConfig(config: JiraServiceDeskNotificationChannelConfig) {
        if (config.configName.isBlank()) {
            throw EventSubscriptionConfigException("Jira config name is required")
        } else {
            jiraConfigurationService.findConfiguration(config.configName)
                ?: throw EventSubscriptionConfigException("Jira config configuration ${config.configName} does not exist")
        }
    }

    override fun mergeConfig(
        a: JiraServiceDeskNotificationChannelConfig,
        changes: JsonNode
    ) = JiraServiceDeskNotificationChannelConfig(
        configName = patchString(changes, a::configName),
        useExisting = patchBoolean(changes, a::useExisting),
        requestStatus = patchNullableEnum(changes, a::requestStatus),
        serviceDeskId = patchInt(changes, a::serviceDeskId),
        requestTypeId = patchInt(changes, a::requestTypeId),
        fields = patchList(changes, a::fields) { it.name },
        searchTerm = patchNullableString(changes, a::searchTerm),
    )

    override val type: String = "jira-service-desk"

    override val displayName: String = "Jira Service Desk"

    override val enabled: Boolean = true

    override fun publish(
        recordId: String,
        config: JiraServiceDeskNotificationChannelConfig,
        event: Event,
        context: Map<String, Any>,
        template: String?,
        outputProgressCallback: (current: JiraServiceDeskNotificationChannelOutput) -> JiraServiceDeskNotificationChannelOutput
    ): NotificationResult<JiraServiceDeskNotificationChannelOutput> {

        // Getting the Jira configuration
        val jiraConfig: JIRAConfiguration = jiraConfigurationService.getConfiguration(config.configName)

        // Initial output
        var output = outputProgressCallback(
            JiraServiceDeskNotificationChannelOutput(
                serviceDeskId = config.serviceDeskId,
                requestTypeId = config.requestTypeId,
            )
        )

        // Gets the Jira client
        val jiraClient = jiraSessionFactory.create(jiraConfig).client
        val serviceDesk = jiraClient.serviceDesk

        // Checking for the existing of the ticket
        if (config.useExisting && !config.searchTerm.isNullOrBlank()) {
            // Rendering the search term
            val searchTerm = eventTemplatingService.renderEvent(
                event = event,
                context = context,
                template = config.searchTerm,
                renderer = PlainEventRenderer.INSTANCE,
            )
            // Query
            val existingStubs = serviceDesk.searchRequest(
                serviceDeskId = config.serviceDeskId,
                requestTypeId = config.requestTypeId,
                searchTerm = searchTerm,
                requestStatus = config.requestStatus ?: JiraServiceDeskRequestStatus.ALL,
            )
            // If more than 1 result, we fail - too ambiguous to carry on
            if (existingStubs.size > 1) {
                error("More than one issue reported for search term: ${config.searchTerm}")
            }
            // If only 1 result, we return it
            else if (existingStubs.size == 1) {
                val existingStub = existingStubs.first()
                return NotificationResult.ok(output.withStub(existingStub).withExisting(true))
            }
            // If not result, we'll create the new request
            else {
                output = outputProgressCallback(output.withExisting(false))
            }
        }

        // Fields
        val fields = config.fields.map { (name, value) ->
            val renderer = if (name == "description") {
                jiraNotificationEventRenderer
            } else {
                PlainEventRenderer.INSTANCE
            }
            JiraCustomField(name, value.transform { text ->
                eventTemplatingService.renderEvent(
                    event = event,
                    context = context,
                    template = text,
                    renderer = renderer,
                )
            })
        }
        output = outputProgressCallback(output.withFields(fields))

        // Creating the service desk request
        val stub = serviceDesk.createRequest(
            serviceDeskId = config.serviceDeskId,
            requestTypeId = config.requestTypeId,
            fields = fields,
        )

        // OK
        return NotificationResult.ok(
            output.withStub(stub)
        )
    }

    override fun toSearchCriteria(text: String): JsonNode = NullNode.instance

}