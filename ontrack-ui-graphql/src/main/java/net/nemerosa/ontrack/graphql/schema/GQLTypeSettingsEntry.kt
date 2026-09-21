package net.nemerosa.ontrack.graphql.schema

import tools.jackson.databind.JsonNode
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.graphql.support.GraphQLBeanConverter
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.settings.SettingsManager
import org.springframework.stereotype.Component

@Component
class GQLTypeSettingsEntry : GQLType {

    override fun getTypeName(): String = "SettingsEntry"

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLBeanConverter.asObjectType(SettingsEntry::class, cache)

    fun <T> createEntry(settingsManager: SettingsManager<T>) = SettingsEntry(
        id = settingsManager.id,
        title = settingsManager.title,
        values = settingsManager.settings.asJson(),
    )

    data class SettingsEntry(
        val id: String,
        val title: String,
        val values: JsonNode,
    )


}