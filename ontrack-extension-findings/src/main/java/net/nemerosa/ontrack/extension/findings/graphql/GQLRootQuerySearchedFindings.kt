package net.nemerosa.ontrack.extension.findings.graphql

import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.model.RankedFinding
import net.nemerosa.ontrack.extension.findings.query.FindingQueryService
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.intArgument
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.stringArgument
import org.springframework.stereotype.Component

/**
 * `searchedFindings(text, size)`: the external IDs of the findings across the projects containing a
 * text.
 */
@Component
class GQLRootQuerySearchedFindings(
    private val findingQueryService: FindingQueryService,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("searchedFindings")
            .description(
                "Security findings of all the projects whose external ID contains the given text, ignoring case, " +
                        "one per external ID, open, accepted or resolved, ranked by the number of projects in which they " +
                        "are open, then by severity, the highest first, then by external ID. A blank text finds nothing. " +
                        "Only the projects whose findings the user is granted the view of are searched. " +
                        "The findings exposed only on disabled branches are left out."
            )
            .argument(stringArgument(ARG_TEXT, "Text the external IDs contain, ignoring case", nullable = false))
            .argument(
                intArgument(
                    ARG_SIZE,
                    "Maximum number of findings to return, at most ${FindingQueryService.MAX_RANKED_FINDINGS}",
                    defaultValue = FindingQueryService.DEFAULT_RANKED_FINDINGS,
                )
            )
            .type(listType(GraphQLTypeReference(RankedFinding::class.java.simpleName)))
            .dataFetcher { env ->
                val text: String = env.getArgument(ARG_TEXT)!!
                val size: Int = env.getArgument(ARG_SIZE) ?: FindingQueryService.DEFAULT_RANKED_FINDINGS
                findingQueryService.getSearchedFindingsAcrossProjects(text = text, size = size)
            }
            .build()

    companion object {
        private const val ARG_TEXT = "text"
        private const val ARG_SIZE = "size"
    }
}
