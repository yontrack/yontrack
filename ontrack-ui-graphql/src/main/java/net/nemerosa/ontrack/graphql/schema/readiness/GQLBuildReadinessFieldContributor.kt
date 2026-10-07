package net.nemerosa.ontrack.graphql.schema.readiness

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.support.stringArgument
import net.nemerosa.ontrack.graphql.support.toNotNull
import net.nemerosa.ontrack.model.readiness.ReadinessService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * Contributes the `readiness` field to builds: what the build still lacks to reach a promotion level
 * or a slot.
 */
@Component
class GQLBuildReadinessFieldContributor(
    private val gqlTypeReadiness: GQLTypeReadiness,
    private val readinessService: ReadinessService,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition>? =
        if (projectEntityType == ProjectEntityType.BUILD) {
            listOf(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name(FIELD)
                    .description(
                        "What this build still lacks to reach a promotion level of its branch, or to be deployed " +
                                "in a slot of its project - exactly one of `$ARG_PROMOTION_LEVEL` and `$ARG_SLOT_ID` " +
                                "must be given. Every missing condition is listed: the validations and promotions " +
                                "required by an auto promotion, the promotion checks, the admission rules of a slot, " +
                                "and what needs a person. A build which already has the promotion level is ready. " +
                                "Computed now from the current state, and read-only: reading it promotes or deploys " +
                                "nothing."
                    )
                    .argument(
                        stringArgument(
                            ARG_PROMOTION_LEVEL,
                            "Name of a promotion level of the build's branch",
                        )
                    )
                    .argument(
                        stringArgument(
                            ARG_SLOT_ID,
                            "ID of a slot of the build's project",
                        )
                    )
                    .type(gqlTypeReadiness.typeRef.toNotNull())
                    .dataFetcher { env ->
                        val build: Build = env.getSource()!!
                        readinessService.getReadiness(
                            build = build,
                            promotionLevel = env.getArgument(ARG_PROMOTION_LEVEL),
                            slotId = env.getArgument(ARG_SLOT_ID),
                        )
                    }
                    .build()
            )
        } else {
            null
        }

    companion object {
        private const val FIELD = "readiness"
        private const val ARG_PROMOTION_LEVEL = "promotionLevel"
        private const val ARG_SLOT_ID = "slotId"
    }
}
