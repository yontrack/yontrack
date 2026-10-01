package net.nemerosa.ontrack.extension.general.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.general.AutoPromotionConditionsService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.support.intArgument
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

/**
 * Contributes the `autoPromotionConditions` field to promotion levels (the conditions alone), to
 * promotion runs (the conditions and their state for the run's build) and to builds (the conditions
 * of a given promotion level and their state for the build, granted or not).
 */
@Component
class AutoPromotionConditionsGQLFieldContributor(
    private val autoPromotionConditionsService: AutoPromotionConditionsService,
    private val structureService: StructureService,
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

        ProjectEntityType.BUILD -> listOf(
            GraphQLFieldDefinition.newFieldDefinition()
                .name(FIELD)
                .description(
                    "Current conditions of the auto promotion of a promotion level of this build's branch, with " +
                            "their current state for this build, whether it is promoted to this level or not - null " +
                            "when the promotion level has no auto promotion"
                )
                .argument(
                    intArgument(
                        ARG_PROMOTION_LEVEL_ID,
                        "ID of the promotion level, which must belong to the build's branch",
                        nullable = false,
                    )
                )
                .type(gqlTypeAutoPromotionBuildConditions.typeRef)
                .dataFetcher { env ->
                    val build: Build = env.getSource()!!
                    val promotionLevelId: Int = env.getArgument(ARG_PROMOTION_LEVEL_ID)!!
                    autoPromotionConditionsService.getBuildConditions(
                        build,
                        structureService.getPromotionLevel(ID.of(promotionLevelId)),
                    )
                }
                .build()
        )

        else -> null
    }

    companion object {
        private const val FIELD = "autoPromotionConditions"
        private const val ARG_PROMOTION_LEVEL_ID = "promotionLevelId"
    }
}
