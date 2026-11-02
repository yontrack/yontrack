package net.nemerosa.ontrack.graphql.schema

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.graphql.support.intArgument
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.links.BranchLinksService
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * Contributes the `consumerLinks` field to the `Branch` type to get the list
 * of its consumers: the branches the source branch is a dependency of.
 */
@Component
class GQLBranchConsumerLinksFieldContributor(
    private val gqlTypeBranchLink: GQLTypeBranchLink,
    private val branchLinksService: BranchLinksService,
) : GQLProjectEntityFieldContributor {
    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType
    ): List<GraphQLFieldDefinition> {
        if (projectEntityType == ProjectEntityType.BRANCH) {
            return listOf(
                linksField()
                    .name("consumerLinks")
                    .build(),
                linksField()
                    .name("upstreamLinks")
                    .deprecate("Removed in V7. Use consumerLinks instead. See #2130")
                    .build(),
            )
        } else {
            return emptyList()
        }
    }

    private fun linksField() = GraphQLFieldDefinition.newFieldDefinition()
        .description("List of branches this branch is a dependency of.")
        .type(listType(gqlTypeBranchLink.typeRef))
        .argument(
            intArgument(
                ARG_BUILDS,
                "Number of builds to use to get the consumers",
            )
        )
        .dataFetcher { env ->
            val branch: Branch = env.getSource()!!
            val builds: Int? = env.getArgument(ARG_BUILDS)
            branchLinksService.getConsumerLinks(branch, builds ?: DEFAULT_BUILDS)
        }

    companion object {
        const val ARG_BUILDS = "builds"
        const val DEFAULT_BUILDS = 5
    }
}
