package net.nemerosa.ontrack.extension.notifications.recording

import com.fasterxml.jackson.databind.JsonNode
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.notifications.channels.GQLEnumNotificationResultType
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.GQLScalarJSON
import net.nemerosa.ontrack.graphql.support.enumArgument
import net.nemerosa.ontrack.graphql.support.intArgument
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.graphql.support.stringArgument
import net.nemerosa.ontrack.model.structure.ProjectEntityID
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

@Component
class GQLRootQueryNotificationRecords(
    private val notificationRecordingService: NotificationRecordingService,
    private val gqlPaginatedListFactory: GQLPaginatedListFactory,
    private val gqlTypeNotificationRecord: GQLTypeNotificationRecord,
    private val gqlEnumNotificationResultType: GQLEnumNotificationResultType,
) : GQLRootQuery {
    override fun getFieldDefinition(): GraphQLFieldDefinition =
        gqlPaginatedListFactory.createRootPaginatedField<NotificationRecord>(
            cache = GQLTypeCache(),
            fieldName = "notificationRecords",
            fieldDescription = "Access to the notification recordings",
            itemType = gqlTypeNotificationRecord.typeName,
            arguments = listOf(
                GraphQLArgument.newArgument()
                    .name(ARG_FILTER_CHANNEL)
                    .description("Filtering on the channel")
                    .type(GraphQLString)
                    .build(),
                GraphQLArgument.newArgument()
                    .name(ARG_FILTER_RESULT_TYPE)
                    .description("Filtering on the result type")
                    .type(gqlEnumNotificationResultType.getTypeRef())
                    .build(),
                stringArgument(ARG_FILTER_SOURCE_ID, "Filtering on the source type"),
                GraphQLArgument.newArgument()
                    .name(ARG_FILTER_SOURCE_DATA)
                    .description("Filtering on the source data")
                    .type(GQLScalarJSON.INSTANCE)
                    .build(),
                enumArgument<ProjectEntityType>(
                    ARG_FILTER_EVENT_ENTITY_TYPE,
                    "Filtering on the entity type targeted by the event (${ARG_FILTER_EVENT_ENTITY_ID} must be provided as well)"
                ),
                intArgument(
                    ARG_FILTER_EVENT_ENTITY_ID,
                    "Filtering on the entity ID targeted by the event (${ARG_FILTER_EVENT_ENTITY_TYPE} must be provided as well)"
                ),
            ),
            itemPaginatedListProvider = { env, offset, size ->
                val resultType = env.getArgument<String>(ARG_FILTER_RESULT_TYPE)?.let {
                    NotificationResultType.valueOf(it)
                }
                val channel: String? = env.getArgument(ARG_FILTER_CHANNEL)
                val sourceId: String? = env.getArgument(ARG_FILTER_SOURCE_ID)
                val sourceData: JsonNode? = env.getArgument(ARG_FILTER_SOURCE_DATA)

                val eventEntityType = env.getArgument<String>(ARG_FILTER_EVENT_ENTITY_TYPE)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ProjectEntityType.valueOf(it) }
                val eventEntityNumericId: Int? = env.getArgument(ARG_FILTER_EVENT_ENTITY_ID)
                val eventEntityId: ProjectEntityID? = if (eventEntityType != null && eventEntityNumericId != null) {
                    ProjectEntityID(eventEntityType, eventEntityNumericId)
                } else {
                    null
                }

                val filter = NotificationRecordFilter(
                    offset = offset,
                    size = size,
                    channel = channel,
                    resultType = resultType,
                    sourceId = sourceId,
                    sourceData = sourceData,
                    eventEntityId = eventEntityId,
                )
                notificationRecordingService.filter(filter)
            }
        )

    companion object {
        private const val ARG_FILTER_CHANNEL = "channel"
        private const val ARG_FILTER_RESULT_TYPE = "resultType"
        private const val ARG_FILTER_SOURCE_ID = "sourceId"
        private const val ARG_FILTER_SOURCE_DATA = "sourceData"
        private const val ARG_FILTER_EVENT_ENTITY_TYPE = "eventEntityType"
        private const val ARG_FILTER_EVENT_ENTITY_ID = "eventEntityId"
    }
}