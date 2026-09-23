package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingObservation
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.findings.query.FindingObservationView
import net.nemerosa.ontrack.extension.findings.query.FindingQueryService
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeProject
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.graphql.support.stringField
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

/**
 * One known weakness at one location of one project.
 */
@Component
class GQLTypeFinding(
    private val findingQueryService: FindingQueryService,
    private val structureService: StructureService,
    private val gqlTypeFindingExposure: GQLTypeFindingExposure,
    private val gqlTypeFindingAcceptance: GQLTypeFindingAcceptance,
    private val paginatedListFactory: GQLPaginatedListFactory,
) : GQLType {

    override fun getTypeName(): String = FINDING

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(
                "One known weakness (a vulnerability, a code issue, a secret, a DAST alert) at one location of one project, " +
                        "identified by its scanner, its external ID and its location."
            )
            .field {
                it.name(Finding::id.name)
                    .description("Unique ID of the finding")
                    .type(GraphQLNonNull(GraphQLInt))
            }
            .field {
                it.name("project")
                    .description("Project the finding belongs to")
                    .type(GraphQLNonNull(GraphQLTypeReference(GQLTypeProject.PROJECT)))
                    .dataFetcher { env ->
                        val finding: Finding = env.getSource()!!
                        structureService.getProject(ID.of(finding.projectId))
                    }
            }
            .stringField(Finding::scanner, "Name of the scanner which reported the finding")
            .stringField(Finding::externalId, "Identifier given by the scanner, like a CVE or a rule ID")
            .stringField(Finding::location, "Where the finding is, without any version. May be empty.")
            .field {
                it.name(Finding::kind.name)
                    .description("Kind of scan which reported the finding")
                    .type(GraphQLNonNull(GraphQLTypeReference(FindingKind::class.java.simpleName)))
            }
            .stringField(Finding::title, "Short description of the finding")
            .field {
                it.name(Finding::url.name)
                    .description("Link to more information about the finding")
                    .type(GraphQLString)
            }
            .field {
                it.name(Finding::firstSeen.name)
                    .description("Time of the first observation")
                    .type(GraphQLNonNull(GQLScalarLocalDateTime.INSTANCE))
            }
            .field {
                it.name(Finding::lastSeen.name)
                    .description("Time of the last observation")
                    .type(GraphQLNonNull(GQLScalarLocalDateTime.INSTANCE))
            }
            .field {
                it.name(Finding::resolvedAt.name)
                    .description("Time the finding was resolved in its project, if it is")
                    .type(GQLScalarLocalDateTime.INSTANCE)
            }
            .field {
                it.name(Finding::maxSeverity.name)
                    .description("Maximum severity across the observations of the finding")
                    .type(GraphQLNonNull(GraphQLTypeReference(FindingSeverity::class.java.simpleName)))
            }
            .field {
                it.name("state")
                    .description(
                        "State of the finding in its project, rolled up from its exposure on the branches which count: " +
                                "the branches matched by the branch model of the project (all of them when it has none), " +
                                "the disabled branches left out. Evaluated when it is read, so that an acceptance past its expiry stops counting."
                    )
                    .type(GraphQLTypeReference(FindingState::class.java.simpleName))
                    .dataFetcher { env ->
                        val finding: Finding = env.getSource()!!
                        findingQueryService.getFindingState(finding)
                    }
            }
            .field {
                it.name("acceptance")
                    .description(
                        "Acceptance recorded by the most recent observation of the finding. " +
                                "Null when this observation carries none, or when all the observations have been purged."
                    )
                    .type(gqlTypeFindingAcceptance.typeRef)
                    .dataFetcher { env ->
                        val finding: Finding = env.getSource()!!
                        findingQueryService.getFindingAcceptance(finding)
                    }
            }
            .field {
                it.name("exposures")
                    .description(
                        "Exposure of the finding on the branches of its project, resolved or not, " +
                                "one entry per branch and validation stamp, by branch name then stamp name"
                    )
                    .type(listType(gqlTypeFindingExposure.typeRef))
                    .dataFetcher { env ->
                        val finding: Finding = env.getSource()!!
                        findingQueryService.getFindingExposures(finding)
                    }
            }
            .field(
                paginatedListFactory.createPaginatedField<Finding, FindingObservationView>(
                    cache = cache,
                    fieldName = "observations",
                    fieldDescription = "Observations of the finding, the most recent first",
                    itemType = FindingObservation::class.java.simpleName,
                    itemPaginatedListProvider = { _, finding, offset, size ->
                        findingQueryService.getFindingObservations(finding, offset, size)
                    }
                )
            )
            .build()

    companion object {
        const val FINDING = "Finding"
    }
}
