package net.nemerosa.ontrack.graphql.schema

import tools.jackson.databind.JsonNode
import graphql.Scalars
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.buildfilter.BuildFilterProviderData
import net.nemerosa.ontrack.model.buildfilter.BuildFilterService
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Component

/**
 * Defines a generic build filter using a `type` and some arbitrary JSON data.
 */
@Component
class GQLInputBuildGenericFilter(
        private val buildFilterService: BuildFilterService
) : GQLInputType<BuildFilterProviderData<*>> {

    override fun getTypeRef() = GraphQLTypeReference(GENERIC_BUILD_FILTER)

    override fun createInputType(dictionary: MutableSet<GraphQLType>): GraphQLInputType {
        return GraphQLInputObjectType.newInputObject()
                .name(GENERIC_BUILD_FILTER)
                .field {
                    it.name(FIELD_TYPE)
                            .description("FQCN of the filter type, null if no filter is to be applied")
                            .type(Scalars.GraphQLString)
                }
                .field {
                    it.name(FIELD_DATA)
                            .description("Filter data as JSON")
                            .type(Scalars.GraphQLString)
                }
                .build()
    }

    fun getFilterType(argument: Any?): String? {
        return if (argument is Map<*, *>) {
            argument[FIELD_TYPE] as String?
        } else {
            null
        }
    }

    fun getFilterData(argument: Any?): JsonNode? {
        return if (argument is Map<*, *>) {
            val data = argument[FIELD_DATA] as String?
            if (data != null && StringUtils.isNotBlank(data)) data.parseAsJson() else null
        } else {
            null
        }
    }

    override fun convert(argument: Any?): BuildFilterProviderData<*> {
        if (argument == null) {
            return buildFilterService.standardFilterProviderData(10).build()
        } else if (argument is Map<*, *>) {
            val type = argument[FIELD_TYPE] as String?
            // Parses and validates the data
            val dataNode = getFilterData(argument)
            // If no type is defined, use the default filter
            if (type == null) {
                return buildFilterService.standardFilterProviderData(10).build()
            }
            // Gets the build filter
            else {
                return buildFilterService.getBuildFilterProviderData<Any>(type, dataNode)
            }
        } else {
            throw IllegalStateException("Unexpected generic filter format")
        }
    }

    companion object {
        val FIELD_TYPE = "type"
        val FIELD_DATA = "data"
        @JvmField
        val GENERIC_BUILD_FILTER = "GenericBuildFilter"
    }

}
