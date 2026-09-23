package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.query.FindingsBranchSummary
import net.nemerosa.ontrack.extension.findings.query.FindingsSummary
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeBranch
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import org.springframework.stereotype.Component

/**
 * Summary of the findings of a project, for its Security section.
 */
@Component
class GQLTypeFindingsSummary : GQLType {

    override fun getTypeName(): String = FindingsSummary::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(
                "Summary of the security findings of a project: its open findings by severity, and their exposure per branch. " +
                        "Every count is by maximum severity, as the filter of the findings is."
            )
            .field {
                it.name(FindingsSummary::open.name)
                    .description("Open findings of the project by maximum severity, every severity present, the most severe first")
                    .type(severityCountListType)
                    .dataFetcher { env -> env.getSource<FindingsSummary>()!!.open.toSeverityCounts() }
            }
            .field {
                it.name(FindingsSummary::openCount.name)
                    .description("Number of open findings of the project")
                    .type(GraphQLNonNull(GraphQLInt))
            }
            .field {
                it.name(FindingsSummary::acceptedCount.name)
                    .description("Number of accepted findings of the project")
                    .type(GraphQLNonNull(GraphQLInt))
            }
            .field {
                it.name(FindingsSummary::resolvedCount.name)
                    .description("Number of resolved findings of the project")
                    .type(GraphQLNonNull(GraphQLInt))
            }
            .field {
                it.name(FindingsSummary::branches.name)
                    .description(
                        "Branches a finding has been exposed on, resolved or not, the most exposed first: " +
                                "by number of open critical findings, then of high ones, and so on, then by name"
                    )
                    .type(GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLTypeReference(BRANCH_SUMMARY)))))
            }
            .field {
                it.name(FindingsSummary::scanners.name)
                    .description("Names of the scanners which reported the findings of the project, sorted")
                    .type(GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLString))))
            }
            .build()

    companion object {
        const val BRANCH_SUMMARY = "FindingsBranchSummary"
        const val SEVERITY_COUNT = "FindingSeverityCount"

        val severityCountListType =
            GraphQLNonNull(GraphQLList(GraphQLNonNull(GraphQLTypeReference(SEVERITY_COUNT))))

        fun Map<FindingSeverity, Int>.toSeverityCounts(): List<FindingSeverityCount> =
            FindingSeverity.entries.map { FindingSeverityCount(it, this[it] ?: 0) }
    }
}

/**
 * Exposure of the findings of a project on one branch.
 */
@Component
class GQLTypeFindingsBranchSummary : GQLType {

    override fun getTypeName(): String = GQLTypeFindingsSummary.BRANCH_SUMMARY

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Exposure of the security findings of a project on one branch")
            .field {
                it.name(FindingsBranchSummary::branch.name)
                    .description("Branch")
                    .type(GraphQLNonNull(GraphQLTypeReference(GQLTypeBranch.BRANCH)))
            }
            .field {
                it.name(FindingsBranchSummary::open.name)
                    .description(
                        "Findings open on this branch — exposed there for one of its validation stamps at least — " +
                                "by maximum severity, every severity present, the most severe first"
                    )
                    .type(GQLTypeFindingsSummary.severityCountListType)
                    .dataFetcher { env ->
                        with(GQLTypeFindingsSummary) {
                            env.getSource<FindingsBranchSummary>()!!.open.toSeverityCounts()
                        }
                    }
            }
            .field {
                it.name(FindingsBranchSummary::openCount.name)
                    .description("Number of findings open on this branch")
                    .type(GraphQLNonNull(GraphQLInt))
            }
            .build()
}

/**
 * Number of findings having a given severity.
 */
data class FindingSeverityCount(
    val severity: FindingSeverity,
    val count: Int,
)

@Component
class GQLTypeFindingSeverityCount : GQLType {

    override fun getTypeName(): String = GQLTypeFindingsSummary.SEVERITY_COUNT

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Number of security findings having a given maximum severity")
            .field {
                it.name(FindingSeverityCount::severity.name)
                    .description("Severity")
                    .type(GraphQLNonNull(GraphQLTypeReference(FindingSeverity::class.java.simpleName)))
            }
            .field {
                it.name(FindingSeverityCount::count.name)
                    .description("Number of findings")
                    .type(GraphQLNonNull(GraphQLInt))
            }
            .build()
}
