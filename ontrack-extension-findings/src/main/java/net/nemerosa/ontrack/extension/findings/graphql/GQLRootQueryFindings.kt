package net.nemerosa.ontrack.extension.findings.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.findings.query.FindingQueryService
import net.nemerosa.ontrack.graphql.schema.GQLRootQuery
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.stringArgument
import org.springframework.stereotype.Component

/**
 * `findings(externalId)`: the security findings having an external ID, across the projects.
 */
@Component
class GQLRootQueryFindings(
    private val findingQueryService: FindingQueryService,
    private val gqlTypeFinding: GQLTypeFinding,
) : GQLRootQuery {

    override fun getFieldDefinition(): GraphQLFieldDefinition =
        GraphQLFieldDefinition.newFieldDefinition()
            .name("findings")
            .description(
                "Security findings having the given external ID (a CVE, a rule ID) across all the projects, " +
                        "by project name. Only the projects whose findings the user is granted the view of are searched."
            )
            .argument(stringArgument(ARG_EXTERNAL_ID, "External ID of the findings", nullable = false))
            .type(listType(gqlTypeFinding.typeRef))
            .dataFetcher { env ->
                val externalId: String = env.getArgument(ARG_EXTERNAL_ID)!!
                findingQueryService.getFindingsByExternalId(externalId)
            }
            .build()

    companion object {
        private const val ARG_EXTERNAL_ID = "externalId"
    }
}
