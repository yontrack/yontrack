package net.nemerosa.ontrack.extension.findings.graphql

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.query.FindingQueryService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * `Project.findings(filter)`: the security findings of a project, paginated.
 */
@Component
class GQLProjectFindingsFieldContributor(
    private val findingQueryService: FindingQueryService,
    private val paginatedListFactory: GQLPaginatedListFactory,
    private val gqlInputFindingFilter: GQLInputFindingFilter,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition>? = if (projectEntityType == ProjectEntityType.PROJECT) {
        listOf(
            paginatedListFactory.createPaginatedField<Project, Finding>(
                cache = GQLTypeCache(),
                fieldName = "findings",
                fieldDescription = "Security findings of the project, the most severe first, then the most recently seen. " +
                        "Empty for a user who is not granted the view of the findings of the project.",
                itemType = GQLTypeFinding.FINDING,
                arguments = listOf(
                    GraphQLArgument.newArgument()
                        .name(ARG_FILTER)
                        .description("Filter on the findings")
                        .type(gqlInputFindingFilter.typeRef)
                        .build()
                ),
                itemPaginatedListProvider = { env, project, offset, size ->
                    val filter = gqlInputFindingFilter.convert(env.getArgument<Any>(ARG_FILTER))
                    findingQueryService.getProjectFindings(project, filter, offset, size)
                }
            )
        )
    } else {
        null
    }

    companion object {
        const val ARG_FILTER = "filter"
    }
}
