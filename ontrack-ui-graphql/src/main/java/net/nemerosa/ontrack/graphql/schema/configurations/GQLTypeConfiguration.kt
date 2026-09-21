package net.nemerosa.ontrack.graphql.schema.configurations

import tools.jackson.databind.JsonNode
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.jsonField
import net.nemerosa.ontrack.graphql.support.stringField
import org.springframework.stereotype.Component

@Component
class GQLTypeConfiguration : GQLType {

    override fun getTypeName(): String = "Configuration"

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Generic configuration")
            .stringField(Data::name)
            .jsonField(Data::data)
            .jsonField(Data::extra)
            .build()

    data class Data(
        @APIDescription("Unique name for the configuration")
        val name: String,
        @APIDescription("Specific data for the configuration (may include the name as well)")
        val data: JsonNode,
        @APIDescription("Addition data outside of the configuration itself")
        val extra: JsonNode?,
    )

}