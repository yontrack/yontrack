package net.nemerosa.ontrack.extension.findings.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.findings.query.FindingQueryService
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.intArgument
import org.springframework.stereotype.Component

/**
 * `finding(id)`: one security finding.
 */
@Component
class GQLRootQueryFinding(
    private val findingQueryService: FindingQueryService,
    private val gqlTypeFinding: GQLTypeFinding,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("finding")
            .description(
                "Security finding by ID. Null when it does not exist, or when the user is not granted " +
                        "the view of the findings of its project."
            )
            .argument(intArgument(ARG_ID, "ID of the finding", nullable = false))
            .type(gqlTypeFinding.typeRef)
            .dataFetcher { env ->
                val id: Int = env.getArgument(ARG_ID)!!
                findingQueryService.findFindingById(id)
            }
            .build()

    companion object {
        private const val ARG_ID = "id"
    }
}
