package net.nemerosa.ontrack.graphql.schema

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.graphql.support.intArgument
import net.nemerosa.ontrack.graphql.support.nullableInputType
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * Getting a project entity using its type and ID.
 */
@Component
class GQLRootQueryEntity(
    private val gqlTypeProjectEntityInformation: GQLTypeProjectEntityInformation,
    private val gqlEnumProjectEntityType: GQLEnumProjectEntityType,
    private val structureService: StructureService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition = GraphQLFieldDefinition.newFieldDefinition()
        .name("entity")
        .description("Getting a project entity using its type and ID.")
        .type(gqlTypeProjectEntityInformation.typeRef)
        .argument {
            it.name("type")
                .description("Type of the entity")
                .type(nullableInputType(gqlEnumProjectEntityType.getTypeRef(), false))
        }
        .argument(intArgument("id", "ID of the entity", nullable = false))
        .dataFetcher { env ->
            val type: ProjectEntityType = ProjectEntityType.valueOf(env.getArgument("type")!!)
            val id: Int = env.getArgument("id")!!
            val entity: ProjectEntity? = type.getFindEntityFn(structureService).apply(ID.of(id))
            entity?.run {
                GQLTypeProjectEntityInformation.Data(
                    type = type,
                    id = id,
                    name = defaultDisplayName,
                    entityName = entityDisplayName,
                    entity = entity,
                )
            }
        }
        .build()

}