package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.GraphQLInt
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import net.nemerosa.ontrack.model.structure.StatsService
import org.springframework.stereotype.Component

/**
 * Getting the count of entities
 */
@Component
class GQLRootQueryEntityCounts(
    private val gqlTypeEntityCounts: GQLTypeEntityCounts
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("entityCounts")
            .description("Collection of entity counts")
            .type(GraphQLNonNull(gqlTypeEntityCounts.typeRef))
            .dataFetcher { EntityCounts() }
            .build()
}

/**
 * Collection of entity counts
 */
@Component
class GQLTypeEntityCounts(
    private val statsService: StatsService
) : GQLType {

    override fun getTypeName(): String = EntityCounts::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Representation of the entity counts")
            .countField("projects", "Number of projects") { statsService.projectCount }
            .countField("branches", "Number of branches") { statsService.branchCount }
            .countField("promotionLevels", "Number of promotion levels") { statsService.promotionLevelCount }
            .countField("validationStamps", "Number of validation stamps") { statsService.validationStampCount }
            .countField("builds", "Number of builds") { statsService.buildCount }
            .countField("promotionRuns", "Number of promotion runs") { statsService.promotionRunCount }
            .countField("validationRuns", "Number of validation runs") { statsService.validationRunCount }
            .build()

    private fun GraphQLObjectType.Builder.countField(
        name: String,
        description: String,
        count: () -> Int,
    ) = field {
        it.name(name)
            .description(description)
            .type(GraphQLNonNull(GraphQLInt))
            .dataFetcher { count() }
    }

}

/**
 * Representation of the entity counts
 */
internal class EntityCounts
