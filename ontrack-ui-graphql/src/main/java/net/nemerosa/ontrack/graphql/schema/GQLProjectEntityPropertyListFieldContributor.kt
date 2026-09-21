package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.Property
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

@Component
class GQLProjectEntityPropertyListFieldContributor(
    private val propertyService: PropertyService,
    private val property: GQLTypeProperty,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType
    ): List<GraphQLFieldDefinition>? {
        return listOf<GraphQLFieldDefinition>(
            GraphQLFieldDefinition.newFieldDefinition()
                .name("properties")
                .description("List of properties")
                .argument(
                    GraphQLArgument.newArgument()
                        .name("type")
                        .description("Fully qualified name of the property type")
                        .type(Scalars.GraphQLString)
                        .build()
                )
                .argument(
                    GraphQLArgument.newArgument()
                        .name("hasValue")
                        .description("Keeps properties having a value")
                        .type(Scalars.GraphQLBoolean)
                        .defaultValue(false)
                        .build()
                )
                .type(listType(property.typeRef))
                .dataFetcher(projectEntityPropertiesDataFetcher(projectEntityClass))
                .build()
        )
    }

    private fun projectEntityPropertiesDataFetcher(projectEntityClass: Class<out ProjectEntity>) =
        DataFetcher { environment: DataFetchingEnvironment ->
            val o = environment.getSource<Any>()
            if (projectEntityClass.isInstance(o)) {
                // Filters
                val typeFilter: String? = environment.getArgument("type")
                val hasValue: Boolean = environment.getArgument<Boolean>("hasValue") ?: false
                // Gets the raw list
                propertyService.getProperties(o as ProjectEntity)
                    .filter { property: Property<*> ->
                        typeFilter?.let {
                            it == property.typeDescriptor.typeName
                        } ?: true
                    }
                    .filter { property: Property<*> ->
                        !hasValue || property.value != null
                    }
                    .sortedBy { it.type.name }
            } else {
                return@DataFetcher null
            }
        }
}
