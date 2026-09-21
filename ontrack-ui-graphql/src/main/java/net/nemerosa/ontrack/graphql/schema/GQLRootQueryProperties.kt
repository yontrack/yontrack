package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.graphql.schema.GQLTypePropertyType.GQLTypePropertyTypeData
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

/**
 * Getting a list of properties
 */
@Component
class GQLRootQueryProperties(
    private val gqlProjectEntityType: GQLEnumProjectEntityType,
    private val gqlPropertyType: GQLTypePropertyType,
    private val propertyService: PropertyService
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("properties")
            .description("Getting a list of properties")
            .type(listType(gqlPropertyType.typeRef))
            .argument {
                it.name(ARG_TYPE)
                    .description("FQCN of the property type")
                    .type(GraphQLString)
            }
            .argument {
                it.name(ARG_PROJECT_ENTITY_TYPE)
                    .description("Project entity supported by the property")
                    .type(gqlProjectEntityType.getTypeRef())
            }
            .dataFetcher { env ->
                val type: String? = env.getArgument(ARG_TYPE)
                val projectEntityType: ProjectEntityType? = env.getArgument<String>(ARG_PROJECT_ENTITY_TYPE)?.let {
                    ProjectEntityType.valueOf(it)
                }
                // Gets the list of properties
                val propertyTypes = if (!type.isNullOrBlank()) {
                    listOf(propertyService.getPropertyTypeByName<Any>(type))
                } else {
                    propertyService.propertyTypes.filter { propertyType ->
                        projectEntityType == null || projectEntityType in propertyType.supportedEntityTypes
                    }
                }
                // Maps them to GQLTypePropertyType
                propertyTypes.map {
                    GQLTypePropertyTypeData(
                        typeName = it::class.java.name,
                        name = it.name,
                        description = it.description,
                        supportedEntityTypes = it.supportedEntityTypes.toList()
                    )
                }.sortedBy { it.name }
            }
            .build()

    companion object {
        const val ARG_PROJECT_ENTITY_TYPE = "projectEntityType"
        const val ARG_TYPE = "type"
    }
}