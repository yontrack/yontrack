package net.nemerosa.ontrack.extension.findings.graphql

import graphql.Scalars.GraphQLBoolean
import graphql.Scalars.GraphQLString
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.findings.model.FindingExposurePeriod
import net.nemerosa.ontrack.extension.findings.model.FindingResolutionReason
import net.nemerosa.ontrack.extension.findings.query.FindingExposurePeriodView
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.GQLTypeValidationRun
import net.nemerosa.ontrack.graphql.support.GQLScalarLocalDateTime
import net.nemerosa.ontrack.graphql.support.GQLScalarLong
import net.nemerosa.ontrack.graphql.support.listType
import org.springframework.stereotype.Component

/**
 * One continuous stretch of an exposure.
 */
@Component
class GQLTypeFindingExposurePeriod(
    private val gqlTypeFindingAcceptedSpan: GQLTypeFindingAcceptedSpan,
) : GQLType {

    override fun getTypeName(): String = FindingExposurePeriod::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(
                "One continuous stretch of an exposure, from the first run which reported the finding to the first run which no longer did. " +
                        "An exposure has one or more periods; a reopening starts a new one."
            )
            .field {
                it.name(FindingExposurePeriod::startedAt.name)
                    .description("Time of the run which started the period")
                    .type(GraphQLNonNull(GQLScalarLocalDateTime.INSTANCE))
                    .dataFetcher { env -> env.view.period.startedAt }
            }
            .field {
                it.name("startedBy")
                    .description("Run which started the period. Null once its build is purged, or for a period older than the periods themselves.")
                    .type(GraphQLTypeReference(GQLTypeValidationRun.VALIDATION_RUN))
                    .dataFetcher { env -> env.view.startedBy }
            }
            .field {
                it.name(FindingExposurePeriod::startedInBuild.name)
                    .description("Display name of the build which started the period: the one of the build while it exists, else the one kept when the period started. Null when unknown.")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.view.startedInBuild }
            }
            .field {
                it.name(FindingExposurePeriod::endedAt.name)
                    .description("Time of the run which ended the period, null while it is open")
                    .type(GQLScalarLocalDateTime.INSTANCE)
                    .dataFetcher { env -> env.view.period.endedAt }
            }
            .field {
                it.name("endedBy")
                    .description("Run which ended the period, the first one no longer reporting the finding. Null once its build is purged, or while the period is open.")
                    .type(GraphQLTypeReference(GQLTypeValidationRun.VALIDATION_RUN))
                    .dataFetcher { env -> env.view.endedBy }
            }
            .field {
                it.name(FindingExposurePeriod::endedInBuild.name)
                    .description("Display name of the build which ended the period, as for startedInBuild")
                    .type(GraphQLString)
                    .dataFetcher { env -> env.view.endedInBuild }
            }
            .field {
                it.name(FindingExposurePeriod::resolutionReason.name)
                    .description("Why the period ended, if it did")
                    .type(GraphQLTypeReference(FindingResolutionReason::class.java.simpleName))
                    .dataFetcher { env -> env.view.period.resolutionReason }
            }
            .field {
                it.name(FindingExposurePeriod::ongoing.name)
                    .description("Whether the period is still open")
                    .type(GraphQLNonNull(GraphQLBoolean))
                    .dataFetcher { env -> env.view.period.ongoing }
            }
            .field {
                it.name(FindingExposurePeriodView::durationSeconds.name)
                    .description("Duration of the period in seconds, until now while it is open. An accepted stretch counts as exposed.")
                    .type(GraphQLNonNull(GQLScalarLong.INSTANCE))
                    .dataFetcher { env -> env.view.durationSeconds }
            }
            .field {
                it.name(FindingExposurePeriodView::acceptedSpans.name)
                    .description("Stretches of the period during which the finding was reported under an acceptance, the oldest first")
                    .type(listType(gqlTypeFindingAcceptedSpan.typeRef))
                    .dataFetcher { env -> env.view.acceptedSpans }
            }
            .build()

    private val DataFetchingEnvironment.view: FindingExposurePeriodView
        get() = getSource()!!
}
