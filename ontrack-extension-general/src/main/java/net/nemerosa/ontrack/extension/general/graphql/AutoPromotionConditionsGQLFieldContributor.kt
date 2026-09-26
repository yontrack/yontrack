package net.nemerosa.ontrack.extension.general.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.general.AutoPromotionConditionsService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import org.springframework.stereotype.Component

/**
 * Contributes the `autoPromotionConditions` field to promotion levels (the conditions alone) and to
 * promotion runs (the conditions and their state for the run's build).
 */
@Component
class AutoPromotionConditionsGQLFieldContributor(
    private val autoPromotionConditionsService: AutoPromotionConditionsService,
    private val gqlTypeAutoPromotionConditions: GQLTypeAutoPromotionConditions,
    private val gqlTypeAutoPromotionBuildConditions: GQLTypeAutoPromotionBuildConditions,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition>? = when (projectEntityType) {
        ProjectEntityType.PROMOTION_LEVEL -> listOf(
            GraphQLFieldDefinition.newFieldDefinition()
                .name(FIELD)
                .description("Current conditions of the auto promotion of this promotion level - null when it has no auto promotion")
                .type(gqlTypeAutoPromotionConditions.typeRef)
                .dataFetcher { env ->
                    val pl: PromotionLevel = env.getSource()!!
                    autoPromotionConditionsService.getConditions(pl)
                }
                .build()
        )

        ProjectEntityType.PROMOTION_RUN -> listOf(
            GraphQLFieldDefinition.newFieldDefinition()
                .name(FIELD)
                .description(
                    "Current conditions of the auto promotion of this run's promotion level, with their current " +
                            "state for this run's build - null when the promotion level has no auto promotion"
                )
                .type(gqlTypeAutoPromotionBuildConditions.typeRef)
                .dataFetcher { env ->
                    val run: PromotionRun = env.getSource()!!
                    autoPromotionConditionsService.getBuildConditions(run)
                }
                .build()
        )

        else -> null
    }

    companion object {
        private const val FIELD = "autoPromotionConditions"
    }
}
