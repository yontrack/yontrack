package net.nemerosa.ontrack.extension.findings.graphql

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.query.BranchFindingsSummary
import net.nemerosa.ontrack.extension.findings.query.FindingQueryService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * `Branch.findingsSummary`: the summary of the security findings of a branch, for the branch page
 * and the dashboards.
 */
@Component
class GQLBranchFindingsFieldContributor(
    private val findingQueryService: FindingQueryService,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition>? = if (projectEntityType == ProjectEntityType.BRANCH) {
        listOf(
            GraphQLFieldDefinition.newFieldDefinition()
                .name("findingsSummary")
                .description(
                    "Summary of the security findings of the branch: its open findings by severity, and the number of " +
                            "its accepted and resolved ones. " +
                            "Null for a user who is not granted the view of the findings of the project."
                )
                .type(GraphQLTypeReference(BranchFindingsSummary::class.java.simpleName))
                .dataFetcher { env ->
                    val branch: Branch = env.getSource()!!
                    findingQueryService.getBranchFindingsSummary(branch)
                }
                .build(),
        )
    } else {
        null
    }
}
