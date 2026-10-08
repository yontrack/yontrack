package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.history.FindingHistoryEntry
import net.nemerosa.ontrack.extension.findings.model.Finding
import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.extension.findings.model.FindingObservation
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.findings.query.FindingExposedFor
import net.nemerosa.ontrack.extension.findings.query.FindingExposedForView
import net.nemerosa.ontrack.extension.findings.query.FindingHistoryEntryView
import net.nemerosa.ontrack.extension.findings.query.FindingObservationFilter
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
import org.dataloader.DataLoader
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * One known weakness at one location of one project.
 */
@Component
class GQLTypeFinding(
    private val findingQueryService: FindingQueryService,
    private val structureService: StructureService,
    private val gqlTypeFindingExposure: GQLTypeFindingExposure,
    private val gqlTypeFindingAcceptance: GQLTypeFindingAcceptance,
    private val gqlTypeFindingSighting: GQLTypeFindingSighting,
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
            .field {
                it.name("firstSeenIn")
                    .description(
                        "Where and when the finding was first seen: the start of the earliest period of its exposures, " +
                                "with its branch, its stamp, its run and its build. Null when it has no exposure."
                    )
                    .type(gqlTypeFindingSighting.typeRef)
                    .dataFetcher { env ->
                        val finding: Finding = env.getSource()!!
                        findingQueryService.getFindingFirstSeenIn(finding)
                    }
            }
            .field {
                it.name("resolvedIn")
                    .description(
                        "Where and when the finding resolved in its project: the latest end of a period of its exposures " +
                                "on the branches which count, with its branch, its stamp, its run and its build. " +
                                "Null unless the finding is resolved in its project."
                    )
                    .type(gqlTypeFindingSighting.typeRef)
                    .dataFetcher { env ->
                        val finding: Finding = env.getSource()!!
                        findingQueryService.getFindingResolvedIn(finding)
                    }
            }
            .field {
                it.name("exposedFor")
                    .description(
                        "How long the finding has been exposed: its longest ongoing period on one branch for one stamp, " +
                                "and the length of its last exposure episode. Over the branches which count toward its state " +
                                "in its project, or over one branch only."
                    )
                    .argument { arg ->
                        arg.name(ARG_BRANCH)
                            .description("Name of the branch whose periods count, instead of the branches which count in the project")
                            .type(GraphQLString)
                    }
                    .type(GraphQLTypeReference(FindingExposedFor::class.java.simpleName))
                    .dataFetcher { env ->
                        val finding: Finding = env.getSource()!!
                        val loader: DataLoader<FindingExposedForKey, FindingExposedForView> =
                            env.dataLoaderRegistry.getDataLoader(FindingExposedForDataLoader.NAME)
                                ?: error("No ${FindingExposedForDataLoader.NAME} data loader is registered.")
                        loader.load(FindingExposedForKey(finding, env.getArgument<String>(ARG_BRANCH)?.takeIf { it.isNotBlank() }))
                    }
            }
            .field(
                paginatedListFactory.createPaginatedField<Finding, FindingObservationView>(
                    cache = cache,
                    fieldName = "observations",
                    fieldDescription = "Observations of the finding, the most recent first, optionally restricted to a branch, " +
                            "a validation stamp and a time range (both ends included)",
                    itemType = FindingObservation::class.java.simpleName,
                    arguments = listOf(
                        GraphQLArgument.newArgument()
                            .name(ARG_BRANCH_ID)
                            .description("ID of the branch of the observations")
                            .type(GraphQLInt)
                            .build(),
                        GraphQLArgument.newArgument()
                            .name(ARG_VALIDATION_STAMP_ID)
                            .description("ID of the validation stamp of the observations")
                            .type(GraphQLInt)
                            .build(),
                        GraphQLArgument.newArgument()
                            .name(ARG_FROM)
                            .description("Earliest time of the observations")
                            .type(GQLScalarLocalDateTime.INSTANCE)
                            .build(),
                        GraphQLArgument.newArgument()
                            .name(ARG_TO)
                            .description("Latest time of the observations")
                            .type(GQLScalarLocalDateTime.INSTANCE)
                            .build(),
                    ),
                    itemPaginatedListProvider = { env, finding, offset, size ->
                        findingQueryService.getFindingObservations(
                            finding = finding,
                            offset = offset,
                            size = size,
                            filter = FindingObservationFilter(
                                branchId = env.getArgument<Int>(ARG_BRANCH_ID),
                                validationStampId = env.getArgument<Int>(ARG_VALIDATION_STAMP_ID),
                                from = env.dateTimeArgument(ARG_FROM),
                                to = env.dateTimeArgument(ARG_TO),
                            ),
                        )
                    }
                )
            )
            .field(
                paginatedListFactory.createPaginatedField<Finding, FindingHistoryEntryView>(
                    cache = cache,
                    fieldName = "history",
                    fieldDescription = "History of the finding, the most recent first: the start and the end of each period of its exposures, " +
                            "the changes of acceptance within them, and its observations, grouped between these.",
                    itemType = FindingHistoryEntry::class.java.simpleName,
                    itemPaginatedListProvider = { _, finding, offset, size ->
                        findingQueryService.getFindingHistory(finding, offset, size)
                    }
                )
            )
            .build()

    private fun DataFetchingEnvironment.dateTimeArgument(name: String): LocalDateTime? =
        when (val value = getArgument<Any>(name)) {
            null -> null
            is LocalDateTime -> value
            else -> Time.fromStorage(value.toString())
        }

    companion object {
        const val FINDING = "Finding"
        private const val ARG_BRANCH = "branch"
        private const val ARG_BRANCH_ID = "branchId"
        private const val ARG_VALIDATION_STAMP_ID = "validationStampId"
        private const val ARG_FROM = "from"
        private const val ARG_TO = "to"
    }
}
