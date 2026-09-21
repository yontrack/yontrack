package net.nemerosa.ontrack.extension.notifications.subscriptions

import tools.jackson.databind.JsonNode
import graphql.Scalars
import graphql.schema.*
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.schema.*
import net.nemerosa.ontrack.graphql.support.*
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

@Component
class EventSubscriptionMutations(
    private val eventSubscriptionService: EventSubscriptionService,
    private val structureService: StructureService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation>
        get() =
            genericMutations +
                    ProjectEntityType.entries.map { type ->
                        createEntitySubscriptionMutation(type)
                    }

    private fun createEntitySubscriptionMutation(type: ProjectEntityType): Mutation =
        object : Mutation {

            override val name: String = "subscribe${type.typeName}ToEvents"

            override val description: String =
                "Set a list of subscriptions on a ${type.displayName} identified by name."

            override fun inputFields(dictionary: MutableSet<GraphQLType>): List<GraphQLInputObjectField> =
                type.names.map {
                    name(it)
                } + listOf(
                    stringInputField(SubscribeToEventsInput::name),
                    stringInputField(SubscribeToEventsInput::channel),
                    jsonInputField(SubscribeToEventsInput::channelConfig),
                    stringListInputField(SubscribeToEventsInput::events),
                    stringInputField(SubscribeToEventsInput::keywords),
                    stringInputField(SubscribeToEventsInput::contentTemplate),
                )

            override val outputFields: List<GraphQLFieldDefinition> = listOf(
                objectField(
                    type = EventSubscriptionPayload::class,
                    name = "subscription",
                    description = "Saved subscription"
                )
            )

            override fun fetch(env: DataFetchingEnvironment): Any {
                // Loads the entity
                val names = type.names.associateWith { name ->
                    getRequiredMutationInputField<String>(env, name)
                }
                val entity: ProjectEntity = type.loadByNames(structureService, names)
                    ?: throw EntityNotFoundByNameException(type, names)
                // Extracting the fields

                // Creates the payload
                val name = getMutationInputField<String>(env, SubscribeToEventsInput::name.name)
                val channel = getRequiredMutationInputField<String>(env, SubscribeToEventsInput::channel.name)
                val channelConfig =
                    getRequiredMutationInputField<JsonNode>(env, SubscribeToEventsInput::channelConfig.name)
                val events = getRequiredMutationInputField<List<String>>(env, SubscribeToEventsInput::events.name)
                val keywords = getMutationInputField<String?>(env, SubscribeToEventsInput::keywords.name)
                val contentTemplate = getMutationInputField<String?>(env, SubscribeToEventsInput::contentTemplate.name)
                val payload = createEventSubscriptionPayload(
                    projectEntity = entity,
                    name = name ?: EventSubscription.computeName(
                        events = events,
                        keywords = keywords,
                        channel = channel,
                        channelConfig = channelConfig,
                        contentTemplate = contentTemplate,
                    ),
                    channel = channel,
                    channelConfig = channelConfig,
                    events = events,
                    keywords = keywords,
                    contentTemplate = contentTemplate,
                )
                // OK
                return mapOf("subscription" to payload)
            }
        }

    private val genericMutations: List<Mutation>
        get() = listOf(

            simpleMutation(
                name = "subscribeToEvents",
                description = "Creates a subscription to a list of events",
                input = SubscribeToEventsInput::class,
                outputName = "subscription",
                outputDescription = "Saved subscription",
                outputType = EventSubscriptionPayload::class,
            ) { input ->
                val projectEntity = input.projectEntity?.run {
                    type.getEntityFn(structureService).apply(ID.of(id))
                }
                createEventSubscriptionPayload(
                    projectEntity = projectEntity,
                    name = input.name ?: EventSubscription.computeName(
                        events = input.events,
                        keywords = input.keywords,
                        channel = input.channel,
                        channelConfig = input.channelConfig,
                        contentTemplate = input.contentTemplate,
                    ),
                    channel = input.channel,
                    channelConfig = input.channelConfig,
                    events = input.events,
                    keywords = input.keywords,
                    contentTemplate = input.contentTemplate,
                )
            },

            unitMutation<DeleteSubscriptionInput>(
                name = "deleteSubscription",
                description = "Deletes an existing subscription using its ID",
            ) { input ->
                val projectEntity = input.projectEntity?.run {
                    type.getEntityFn(structureService).apply(ID.of(id))
                }
                eventSubscriptionService.deleteSubscriptionByName(projectEntity, input.id)
            },

            simpleMutation(
                name = "disableSubscription",
                description = "Disables an existing subscription",
                input = DisableSubscriptionInput::class,
                outputName = "subscription",
                outputDescription = "Saved subscription",
                outputType = EventSubscriptionPayload::class,
            ) { input ->
                val projectEntity = input.projectEntity?.run {
                    type.getEntityFn(structureService).apply(ID.of(id))
                }
                val name = input.name
                eventSubscriptionService.findSubscriptionByName(
                    projectEntity = projectEntity,
                    name = name,
                )?.let { subscription ->
                    eventSubscriptionService.disableSubscriptionByName(projectEntity, name)
                    EventSubscriptionPayload(
                        name = subscription.name,
                        channel = subscription.channel,
                        channelConfig = subscription.channelConfig,
                        events = subscription.events.toList(),
                        keywords = subscription.keywords,
                        disabled = true,
                        origin = subscription.origin,
                        contentTemplate = subscription.contentTemplate,
                    )
                }
            },

            simpleMutation(
                name = "enableSubscription",
                description = "Enables an existing subscription",
                input = EnableSubscriptionInput::class,
                outputName = "subscription",
                outputDescription = "Saved subscription",
                outputType = EventSubscriptionPayload::class,
            ) { input ->
                val projectEntity = input.projectEntity?.run {
                    type.getEntityFn(structureService).apply(ID.of(id))
                }
                val name = input.name
                eventSubscriptionService.findSubscriptionByName(
                    projectEntity = projectEntity,
                    name = name,
                )?.let { subscription ->
                    eventSubscriptionService.enableSubscriptionByName(projectEntity, name)
                    EventSubscriptionPayload(
                        name = subscription.name,
                        channel = subscription.channel,
                        channelConfig = subscription.channelConfig,
                        events = subscription.events.toList(),
                        keywords = subscription.keywords,
                        disabled = false,
                        origin = subscription.origin,
                        contentTemplate = subscription.contentTemplate,
                    )
                }
            },

            simpleMutation(
                name = "renameSubscription",
                description = "Renames an existing subscription",
                input = RenameSubscriptionInput::class,
                outputName = "subscription",
                outputDescription = "Renamed subscription",
                outputType = EventSubscriptionPayload::class,
            ) { input ->
                val projectEntity = input.projectEntity?.run {
                    type.getEntityFn(structureService).apply(ID.of(id))
                }
                eventSubscriptionService.findSubscriptionByName(
                    projectEntity = projectEntity,
                    name = input.name,
                )?.let { subscription ->
                    eventSubscriptionService.renameSubscription(projectEntity, input.name, input.newName)?.run {
                        EventSubscriptionPayload(
                            name = name,
                            channel = channel,
                            channelConfig = channelConfig,
                            events = events.toList(),
                            keywords = keywords,
                            disabled = disabled,
                            origin = origin,
                            contentTemplate = contentTemplate,
                        )
                    }
                }
            },
        )

    private fun createEventSubscriptionPayload(
        projectEntity: ProjectEntity?,
        name: String,
        channel: String,
        channelConfig: JsonNode,
        events: List<String>,
        keywords: String?,
        contentTemplate: String?,
    ): EventSubscriptionPayload {
        eventSubscriptionService.subscribe(
            EventSubscription(
                name = name,
                channel = channel,
                channelConfig = channelConfig,
                events = events.toSet(),
                projectEntity = projectEntity,
                keywords = keywords,
                disabled = false,
                origin = EventSubscriptionOrigins.API,
                contentTemplate = contentTemplate?.trimIndent(),
            )
        )
        return EventSubscriptionPayload(
            name = name,
            channel = channel,
            channelConfig = channelConfig,
            events = events,
            keywords = keywords,
            disabled = false,
            origin = EventSubscriptionOrigins.API,
            contentTemplate = contentTemplate,
        )
    }

    private fun name(name: String): GraphQLInputObjectField = GraphQLInputObjectField.newInputObjectField()
        .name(name)
        .description("${name.replaceFirstChar { it.titlecase() }} name")
        .type(GraphQLNonNull(Scalars.GraphQLString))
        .build()
}

@APIDescription("Subscription deletion")
data class DeleteSubscriptionInput(
    @APIDescription("Target project entity (null for global events)")
    @TypeRef(embedded = true, suffix = "Input")
    val projectEntity: ProjectEntityID?,
    @APIDescription("ID of the subscription to delete")
    val id: String,
)

@APIDescription("Subscription disabling")
data class DisableSubscriptionInput(
    @APIDescription("Target project entity (null for global events)")
    @TypeRef(embedded = true, suffix = "Input")
    val projectEntity: ProjectEntityID?,
    @APIDescription("Name of the subscription to disable")
    val name: String,
)

@APIDescription("Subscription enabling")
data class EnableSubscriptionInput(
    @APIDescription("Target project entity (null for global events)")
    @TypeRef(embedded = true, suffix = "Input")
    val projectEntity: ProjectEntityID?,
    @APIDescription("Name of the subscription to enable")
    val name: String,
)

@APIDescription("Renaming a subscription")
data class RenameSubscriptionInput(
    @APIDescription("Target project entity (null for global events)")
    @TypeRef(embedded = true, suffix = "Input")
    val projectEntity: ProjectEntityID?,
    @APIDescription("Name of the subscription to rename")
    val name: String,
    @APIDescription("New name to set")
    val newName: String,
)

@APIDescription("Subscription to events")
data class SubscribeToEventsInput(
    @APIDescription("Target project entity (null for global events)")
    @TypeRef(embedded = true, suffix = "Input")
    val projectEntity: ProjectEntityID?,
    @APIDescription("Unique name of the channel in its scope (null for backward compatibility, will be required in V5)")
    val name: String? = null,
    @APIDescription("Channel to send this event to")
    val channel: String,
    @APIDescription("Channel configuration")
    val channelConfig: JsonNode,
    @APIDescription("List of events types to subscribe to")
    @ListRef
    val events: List<String>,
    @APIDescription("Optional space-separated list of tokens to look for in the events")
    val keywords: String?,
    @APIDescription("Optional template to use for the message")
    val contentTemplate: String?,
)

