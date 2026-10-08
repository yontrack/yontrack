package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.history.FindingHistoryEntry
import net.nemerosa.ontrack.extension.findings.history.FindingHistoryEntryType
import net.nemerosa.ontrack.extension.findings.model.FindingResolutionReason
import net.nemerosa.ontrack.extension.findings.query.FindingHistoryEntryView
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeBranch
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeValidationRun
import net.nemerosa.ontrack.graphql.schema.GQLTypeValidationStamp
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import org.springframework.stereotype.Component

/**
 * Entry in the history of a finding.
 */
@Component
class GQLTypeFindingHistoryEntry(
    private val gqlTypeFindingAcceptance: GQLTypeFindingAcceptance,
) : GQLType {

    override fun getTypeName(): String = FindingHistoryEntry::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(
                "Entry in the history of a security finding: the start or the end of a period of exposure, " +
                        "a change of acceptance, or a group of observations between two of these."
            )
            .field {
                it.name(FindingHistoryEntry::type.name)
                    .description("Type of the entry")
                    .type(GraphQLNonNull(GraphQLTypeReference(FindingHistoryEntryType::class.java.simpleName)))
                    .dataFetcher { env -> env.view.entry.type }
            }
            .field {
                it.name(FindingHistoryEntry::time.name)
                    .description("Time of the entry; for a group of observations, the time of the last one")
                    .type(GraphQLNonNull(GQLScalarLocalDateTime.INSTANCE))
                    .dataFetcher { env -> env.view.entry.time }
            }
            .field {
                it.name("branch")
                    .description("Branch of the entry")
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
                it.name("validationRun")
                    .description("Run of the entry. Null once its build is purged, or for an entry no run made, like an expiry.")
                    .type(GraphQLTypeReference(GQLTypeValidationRun.VALIDATION_RUN))
                    .dataFetcher { env -> env.view.validationRun }
            }
            .field {
                it.name(FindingHistoryEntry::build.name)
                    .description("Display name of the build of the run, or the one kept by the period. Null when unknown.")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.view.build }
            }
            .field {
                it.name(FindingHistoryEntry::resolutionReason.name)
                    .description("For a resolution, its reason")
                    .type(GraphQLTypeReference(FindingResolutionReason::class.java.simpleName))
                    .dataFetcher { env -> env.view.entry.resolutionReason }
            }
            .field {
                it.name(FindingHistoryEntry::acceptance.name)
                    .description("For an acceptance, or its expiry, the acceptance")
                    .type(gqlTypeFindingAcceptance.typeRef)
                    .dataFetcher { env -> env.view.entry.acceptance }
            }
            .field {
                it.name(FindingHistoryEntry::count.name)
                    .description("For a group of observations, their number; 0 otherwise")
                    .type(GraphQLNonNull(GraphQLInt))
                    .dataFetcher { env -> env.view.entry.count }
            }
            .field {
                it.name(FindingHistoryEntry::firstTime.name)
                    .description("For a group of observations, the time of the first one")
                    .type(GQLScalarLocalDateTime.INSTANCE)
                    .dataFetcher { env -> env.view.entry.firstTime }
            }
            .field {
                it.name(FindingHistoryEntry::lastTime.name)
                    .description("For a group of observations, the time of the last one")
                    .type(GQLScalarLocalDateTime.INSTANCE)
                    .dataFetcher { env -> env.view.entry.lastTime }
            }
            .field {
                it.name(FindingHistoryEntryView::firstBuild.name)
                    .description("For a group of observations, the display name of the build of the first one")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.view.firstBuild }
            }
            .field {
                it.name(FindingHistoryEntryView::lastBuild.name)
                    .description("For a group of observations, the display name of the build of the last one")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.view.lastBuild }
            }
            .field {
                it.name(FindingHistoryEntryView::firstValidationRun.name)
                    .description("For a group of observations, the run of the first one")
                    .type(GraphQLTypeReference(GQLTypeValidationRun.VALIDATION_RUN))
                    .dataFetcher { env -> env.view.firstValidationRun }
            }
            .field {
                it.name(FindingHistoryEntryView::lastValidationRun.name)
                    .description("For a group of observations, the run of the last one")
                    .type(GraphQLTypeReference(GQLTypeValidationRun.VALIDATION_RUN))
                    .dataFetcher { env -> env.view.lastValidationRun }
            }
            .build()

    private val DataFetchingEnvironment.view: FindingHistoryEntryView
        get() = getSource()!!
}
