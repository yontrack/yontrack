package net.nemerosa.ontrack.extension.scorecard.graphql

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.scorecard.service.Scorecard
import net.nemerosa.ontrack.extension.scorecard.service.ScorecardService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * `Project.scorecard`: the readings of the project.
 */
@Component
class GQLProjectScorecardFieldContributor(
    private val scorecardService: ScorecardService,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition>? = if (projectEntityType == ProjectEntityType.PROJECT) {
        listOf(
            GraphQLFieldDefinition.newFieldDefinition()
                .name("scorecard")
                .description("Delivery scorecard of the project: its readings in every set it is in, with their daily history")
                .type(GraphQLNonNull(GraphQLTypeReference(Scorecard::class.java.simpleName)))
                .dataFetcher { env ->
                    val project: Project = env.getSource()!!
                    scorecardService.getScorecard(project)
                }
                .build()
        )
    } else {
        null
    }
}
