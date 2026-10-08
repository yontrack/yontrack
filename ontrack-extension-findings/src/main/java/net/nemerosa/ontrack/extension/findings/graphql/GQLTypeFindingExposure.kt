package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLString
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.findings.model.FindingExposure
import net.nemerosa.ontrack.extension.findings.model.FindingExposureState
import net.nemerosa.ontrack.extension.findings.model.FindingResolutionReason
import net.nemerosa.ontrack.extension.findings.query.FindingExposureView
import net.nemerosa.ontrack.extension.findings.query.FindingQueryService
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeBranch
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeValidationStamp
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import net.nemerosa.ontrack.graphql.support.listType
import org.springframework.stereotype.Component

/**
 * Exposure of a finding on a branch, for the scans of one stamp.
 */
@Component
class GQLTypeFindingExposure(
    private val findingQueryService: FindingQueryService,
    private val gqlTypeFindingExposurePeriod: GQLTypeFindingExposurePeriod,
) : GQLType {

    override fun getTypeName(): String = FindingExposure::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("A security finding is exposed on a branch while the latest scan of the same stamp on that branch reports it.")
            .field {
                it.name("branch")
                    .description("Branch the finding is exposed on")
                    .type(GraphQLNonNull(GraphQLTypeReference(GQLTypeBranch.BRANCH)))
                    .dataFetcher { env -> env.view.branch }
            }
            .field {
                it.name("validationStamp")
                    .description("Validation stamp of the scans")
                    .type(GraphQLNonNull(GraphQLTypeReference(GQLTypeValidationStamp.VALIDATION_STAMP)))
                    .dataFetcher { env -> env.view.validationStamp }
            }
            .field {
                it.name(FindingExposure::since.name)
                    .description("Time the current exposure started")
                    .type(GraphQLNonNull(GQLScalarLocalDateTime.INSTANCE))
                    .dataFetcher { env -> env.view.exposure.since }
            }
            .field {
                it.name("state")
                    .description("State of the exposure today: an acceptance past its expiry stops counting when it is read.")
                    .type(GraphQLNonNull(GraphQLTypeReference(FindingExposureState::class.java.simpleName)))
                    .dataFetcher { env -> env.view.exposure.stateOn(Time.now.toLocalDate()) }
            }
            .field {
                it.name(FindingExposureView::counts.name)
                    .description(
                        "Whether the branch counts toward the state of the finding in its project: " +
                                "matched by the branch model of the project (every branch when it has none, without SCM), " +
                                "and not disabled. A branch which does not count still exposes the finding."
                    )
                    .type(GraphQLNonNull(GraphQLBoolean))
                    .dataFetcher { env -> env.view.counts }
            }
            .field {
                it.name(FindingExposure::accepted.name)
                    .description("Whether the latest scan reported the finding under an acceptance holding on the day of the scan")
                    .type(GraphQLNonNull(GraphQLBoolean))
                    .dataFetcher { env -> env.view.exposure.accepted }
            }
            .field {
                it.name(FindingExposure::acceptanceExpiresAt.name)
                    .description("Last day this acceptance holds, as an ISO date (YYYY-MM-DD), if any")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.view.exposure.acceptanceExpiresAt?.toString() }
            }
            .field {
                it.name(FindingExposure::resolvedAt.name)
                    .description("Time the finding was resolved on this branch for this stamp, if it is")
                    .type(GQLScalarLocalDateTime.INSTANCE)
                    .dataFetcher { env -> env.view.exposure.resolvedAt }
            }
            .field {
                it.name(FindingExposure::resolutionReason.name)
                    .description("Why the finding was resolved, if it is")
                    .type(GraphQLTypeReference(FindingResolutionReason::class.java.simpleName))
                    .dataFetcher { env -> env.view.exposure.resolutionReason }
            }
            .field {
                it.name("periods")
                    .description(
                        "Periods of the exposure, the oldest first: one per stretch from the first run which reported the finding " +
                                "to the first run which no longer did. A reopening starts a new period."
                    )
                    .type(listType(gqlTypeFindingExposurePeriod.typeRef))
                    .dataFetcher { env -> findingQueryService.getExposurePeriods(env.view) }
            }
            .build()

    private val DataFetchingEnvironment.view: FindingExposureView
        get() = getSource()!!
}
