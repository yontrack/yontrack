package net.nemerosa.ontrack.extension.scorecard.graphql

import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.service.ScorecardService
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.*
import org.springframework.stereotype.Component

/**
 * One reading of a project in one set: its latest snapshot, and its daily history.
 */
@Component
class GQLTypeReading(
    private val scorecardService: ScorecardService,
) : GQLType {

    override fun getTypeName(): String = READING

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description(
                "One measurement of one project at one moment, taken by Yontrack from its own data, for one set. " +
                        "Durations are in seconds, frequencies per week."
            )
            .field {
                it.name(Reading::key.name)
                    .description("Key of the reading, like delivery.leadTime")
                    .type(GraphQLNonNull(GraphQLString))
            }
            .field {
                it.name(Reading::day.name)
                    .description("Day of the snapshot, as an ISO date (YYYY-MM-DD)")
                    .type(GraphQLNonNull(GraphQLString))
                    .dataFetcher { env -> env.getSource<Reading>()!!.day.toString() }
            }
            .localDateTimeField(Reading::computedAt, "When the reading was computed")
            .localDateTimeField(Reading::windowStart, "Start of the window the reading was taken over")
            .localDateTimeField(Reading::windowEnd, "End of the window the reading was taken over")
            .doubleField(Reading::value, "Value of the reading, null when it is unknown. The median for a duration.")
            .field {
                it.name(Reading::basis.name)
                    .description("What the value rests on")
                    .type(GraphQLNonNull(GraphQLTypeReference(ReadingBasis::class.java.simpleName)))
            }
            .field {
                it.name(Reading::unknownReason.name)
                    .description("Why the reading is unknown, null when it is not")
                    .type(GraphQLTypeReference(ReadingUnknownReason::class.java.simpleName))
            }
            .jsonField(
                Reading::details,
                "What explains the value: the sample count (count), the other statistics of a duration (p90, mean, min, max), " +
                        "the kind of marker (markerKind), the marker (marker) and the branches read (scope)."
            )
            .field {
                it.name("history")
                    .description("Daily snapshots of this reading over the last days, oldest first, this one included")
                    .argument(
                        GraphQLArgument.newArgument()
                            .name(ARG_DAYS)
                            .description("Number of days back from today")
                            .type(GraphQLInt)
                            .defaultValueProgrammatic(DEFAULT_HISTORY_DAYS)
                    )
                    .type(listType(GraphQLTypeReference(READING)))
                    .dataFetcher { env ->
                        val reading = env.getSource<Reading>()!!
                        val days = env.getArgument<Int>(ARG_DAYS) ?: DEFAULT_HISTORY_DAYS
                        scorecardService.getHistory(reading, days)
                    }
            }
            .build()

    companion object {
        const val READING = "Reading"
        const val ARG_DAYS = "days"
        const val DEFAULT_HISTORY_DAYS = 90
    }
}
