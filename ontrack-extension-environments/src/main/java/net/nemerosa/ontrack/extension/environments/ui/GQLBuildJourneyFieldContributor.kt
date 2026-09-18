package net.nemerosa.ontrack.extension.environments.ui

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.environments.BuildSlotJourney
import net.nemerosa.ontrack.extension.environments.EnvironmentsLicense
import net.nemerosa.ontrack.extension.environments.service.SlotStatusService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.support.typedListField
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * `Build.journey` - this build's state in every slot of its project, in environment order.
 *
 * It is one field rather than the caller stitching `eligibleSlots`, `currentDeployments` and the
 * build's pipelines together, because those three answers can disagree about the same slot and the
 * journey strip has to show exactly one state per slot.
 */
@Component
class GQLBuildJourneyFieldContributor(
    private val environmentsLicense: EnvironmentsLicense,
    private val slotStatusService: SlotStatusService,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType
    ): List<GraphQLFieldDefinition>? =
        if (projectEntityType == ProjectEntityType.BUILD && environmentsLicense.environmentFeatureEnabled) {
            listOf(
                typedListField<Build, BuildSlotJourney>(
                    type = BuildSlotJourney::class,
                    name = "journey",
                    description = "State of this build in every slot of its project, in environment order",
                ) { build ->
                    slotStatusService.getBuildJourney(build)
                }
            )
        } else {
            null
        }

}
