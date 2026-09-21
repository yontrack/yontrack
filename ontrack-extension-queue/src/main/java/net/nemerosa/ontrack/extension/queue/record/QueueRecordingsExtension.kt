package net.nemerosa.ontrack.extension.queue.record

import tools.jackson.databind.JsonNode
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.queue.QueueExtensionFeature
import net.nemerosa.ontrack.extension.recordings.RecordingsExtension
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class QueueRecordingsExtension(
        queueExtensionFeature: QueueExtensionFeature
) : AbstractExtension(queueExtensionFeature), RecordingsExtension<QueueRecord, QueueRecordQueryFilter> {

    override val id: String = "queue"

    override val graphQLPrefix: String = "Queue"

    override val displayName: String = "Queue messages"

    override fun toJson(recording: QueueRecord): JsonNode = recording.asJson()

    override fun fromJson(data: JsonNode): QueueRecord = data.parse()

    override fun graphQLRecordFields(cache: GQLTypeCache): List<GraphQLFieldDefinition> =
            GraphQLBeanConverter.asObjectFields(QueueRecord::class, cache)

    override val filterType: KClass<QueueRecordQueryFilter> = QueueRecordQueryFilter::class

    override fun filterQuery(filter: QueueRecordQueryFilter, queryVariables: MutableMap<String, Any?>): List<String> {
        val queries = mutableListOf<String>()

        if (!filter.id.isNullOrBlank()) {
            queries += "data::jsonb->>'id' = :id"
            queryVariables["id"] = filter.id
        }

        if (!filter.processor.isNullOrBlank()) {
            queries += "data::jsonb->'data'->'queuePayload'->>'processor' = :processor"
            queryVariables["processor"] = filter.processor
        }

        if (filter.state != null) {
            queries += "data::jsonb->'data'->>'state' = :state"
            queryVariables["state"] = filter.state.name
        }

        if (!filter.routingKey.isNullOrBlank()) {
            queries += "data::jsonb->'data'->>'routingKey' = :routingKey"
            queryVariables["routingKey"] = filter.routingKey
        }

        if (!filter.queueName.isNullOrBlank()) {
            queries += "data::jsonb->'data'->>'queueName' = :queueName"
            queryVariables["queueName"] = filter.queueName
        }

        if (!filter.username.isNullOrBlank()) {
            queries += "data::jsonb->'data'->>'username' = :username"
            queryVariables["username"] = filter.username
        }

        if (!filter.text.isNullOrBlank()) {
            queries += "(data::jsonb->'data'->'queuePayload'->>'body' LIKE :text) OR (data::jsonb->'data'->>'actualPayload' LIKE :text)"
            queryVariables["text"] = "%${filter.text}%"
        }

        return queries
    }

}