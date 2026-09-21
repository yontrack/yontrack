package net.nemerosa.ontrack.graphql.schema

import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.graphql.support.*
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

@Component
class GQLTypeProjectEntityInformation(
    private val gqlProjectEntityInterface: GQLProjectEntityInterface,
    private val gqlTypeProperty: GQLTypeProperty,
    private val propertyService: PropertyService,
) : GQLType {

    data class Data(
        @APIDescription("Type of the project entity")
        val type: ProjectEntityType,
        @APIDescription("ID of the project entity")
        val id: Int,
        @APIDescription("Name of the project entity")
        val name: String,
        @APIDescription("Full name of the project entity")
        val entityName: String,
        @APIDescription("Project entity")
        val entity: ProjectEntity,
    )

    override fun getTypeName(): String = "ProjectEntityInformation"

    override fun createType(cache: GQLTypeCache): GraphQLObjectType = GraphQLObjectType.newObject()
        .name(typeName)
        .description("Information about a project entity")
        .enumField(Data::type)
        .intField(Data::id)
        .stringField(Data::name)
        .stringField(Data::entityName)
        .field {
            it.name(Data::entity.name)
                .description(getPropertyDescription(Data::entity))
                .type(gqlProjectEntityInterface.typeRef.toNotNull())
        }
        .field {
            it.name("properties")
                .description("List of properties for this entity")
                .type(listType(gqlTypeProperty.typeRef))
                .argument(booleanArgument(ARG_HAS_VALUE, "Keeps only properties which have a value"))
                .dataFetcher { env ->
                    val data: Data = env.getSource()!!
                    val hasValue: Boolean = env.getArgument<Boolean>("hasValue") ?: false
                    propertyService.getProperties(data.entity)
                        .filter { property ->
                            !hasValue || property.value != null
                        }
                }
        }
        .build()

    companion object {
        const val ARG_HAS_VALUE = "hasValue"
    }

}