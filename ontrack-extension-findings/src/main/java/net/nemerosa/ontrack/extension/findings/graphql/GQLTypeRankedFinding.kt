package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.model.RankedFinding
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import org.springframework.stereotype.Component

/**
 * One external ID of the findings of some projects, ranked by the number of these projects in
 * which it is open.
 */
@Component
class GQLTypeRankedFinding : GQLType {

    override fun getTypeName(): String = RankedFinding::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(
                "One external ID of the security findings of some projects, with the number of these projects in which it is " +
                        "open, accepted or resolved, each project counted once, by its most exposed finding."
            )
            .field {
                it.name(RankedFinding::externalId.name)
                    .description("External ID of the findings, like a CVE or a rule ID")
                    .type(GraphQLNonNull(GraphQLString))
            }
            .field {
                it.name(RankedFinding::title.name)
                    .description("Title of the findings, the one of the most exposed, then most severe, of them")
                    .type(GraphQLNonNull(GraphQLString))
            }
            .field {
                it.name(RankedFinding::severity.name)
                    .description("Highest maximum severity of the findings")
                    .type(GraphQLNonNull(GraphQLTypeReference(FindingSeverity::class.java.simpleName)))
            }
            .field {
                it.name(RankedFinding::openProjects.name)
                    .description("Number of projects in which the finding is open")
                    .type(GraphQLNonNull(GraphQLInt))
            }
            .field {
                it.name(RankedFinding::acceptedProjects.name)
                    .description("Number of projects in which the finding is accepted, and not open")
                    .type(GraphQLNonNull(GraphQLInt))
            }
            .field {
                it.name(RankedFinding::resolvedProjects.name)
                    .description("Number of projects in which the finding is resolved: neither open nor accepted")
                    .type(GraphQLNonNull(GraphQLInt))
            }
            .field {
                it.name(RankedFinding::firstSeen.name)
                    .description("Earliest time any of the findings was first seen")
                    .type(GraphQLNonNull(GQLScalarLocalDateTime.INSTANCE))
            }
            .build()
}
