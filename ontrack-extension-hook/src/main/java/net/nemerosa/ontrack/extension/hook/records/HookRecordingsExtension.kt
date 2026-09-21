package net.nemerosa.ontrack.extension.hook.records

import tools.jackson.databind.JsonNode
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.hook.HookExtensionFeature
import net.nemerosa.ontrack.extension.recordings.RecordingsExtension
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class HookRecordingsExtension(
        extensionFeature: HookExtensionFeature,
) : AbstractExtension(extensionFeature), RecordingsExtension<HookRecord, HookRecordQueryFilter> {

    override val id: String = "hook"

    override val graphQLPrefix: String = "Hook"

    override val displayName: String = "Hook messages"

    override fun graphQLRecordFields(cache: GQLTypeCache): List<GraphQLFieldDefinition> =
            GraphQLBeanConverter.asObjectFields(HookRecord::class, cache)

    override val filterType: KClass<HookRecordQueryFilter> = HookRecordQueryFilter::class

    override fun fromJson(data: JsonNode): HookRecord = data.parse()

    override fun filterQuery(filter: HookRecordQueryFilter, queryVariables: MutableMap<String, Any?>): List<String> {
        val queries = mutableListOf<String>()

        if (!filter.id.isNullOrBlank()) {
            queries += "data::jsonb->>'id' = :id"
            queryVariables["id"] = filter.id
        }

        if (!filter.hook.isNullOrBlank()) {
            queries += "data::jsonb->'data'->>'hook' = :hook"
            queryVariables["hook"] = filter.hook
        }

        if (filter.state != null) {
            queries += "data::jsonb->'data'->>'state' = :state"
            queryVariables["state"] = filter.state.name
        }

        if (!filter.text.isNullOrBlank()) {
            queries += "data::jsonb->'data'->'request'->>'body' LIKE :text"
            queryVariables["text"] = "%${filter.text}%"
        }

        return queries
    }

    override fun toJson(recording: HookRecord): JsonNode = recording.asJson()
}