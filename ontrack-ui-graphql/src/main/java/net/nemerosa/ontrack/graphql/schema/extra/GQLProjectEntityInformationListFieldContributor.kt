package net.nemerosa.ontrack.graphql.schema.extra

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.api.EntityInformationExtension
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.Property
import net.nemerosa.ontrack.model.structure.PropertyService
import org.springframework.stereotype.Component

@Component
class GQLProjectEntityInformationListFieldContributor(
    private val propertyService: PropertyService,
    private val information: GQLTypeEntityInformation,
    private val extensionManager: ExtensionManager,
) : GQLProjectEntityFieldContributor {

    private val extensions: Collection<EntityInformationExtension> by lazy {
        extensionManager.getExtensions(EntityInformationExtension::class.java)
    }

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType
    ): List<GraphQLFieldDefinition>? {
        return listOf<GraphQLFieldDefinition>(
            GraphQLFieldDefinition.newFieldDefinition()
                .name("information")
                .description("List of information components attached to this entity")
                .type(listType(information.typeRef))
                .dataFetcher { env ->
                    val entity: ProjectEntity = env.getSource()!!
                    extensions.mapNotNull { it.getInformation(entity) }
                }
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
            } else {
                return@DataFetcher null
            }
        }
}
