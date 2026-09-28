package net.nemerosa.ontrack.extension.scorecard.graphql

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.scorecard.service.ScorecardService
import net.nemerosa.ontrack.graphql.schema.Mutation
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

@Component
class ScorecardMutations(
    private val structureService: StructureService,
    private val scorecardService: ScorecardService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation> = listOf(
        unitMutation(
            name = "recomputeProjectScorecard",
            description = "Queues the recompute of the readings of a project, in every set it is in, " +
                    "overwriting the snapshots of the day. Needs the right to configure the project.",
            input = RecomputeProjectScorecardInput::class,
        ) { input ->
            val project = structureService.getProject(ID.of(input.projectId))
            scorecardService.recompute(project)
        }
    )
}

data class RecomputeProjectScorecardInput(
    @APIDescription("ID of the project")
    val projectId: Int,
)
