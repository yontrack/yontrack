package net.nemerosa.ontrack.extension.indicators.ui.graphql

import graphql.Scalars.GraphQLInt
import graphql.Scalars.GraphQLString
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.indicators.model.Rating
import java.time.Duration

typealias DefinitionBuilder = GraphQLFieldDefinition.Builder

private const val ARG_DURATION = "duration"
private const val ARG_RATE = "rate"

fun DefinitionBuilder.durationArgument(): DefinitionBuilder =
    argument {
        it.name(ARG_DURATION)
            .description("Number of seconds (> 0) to get indicator trends from.")
            .type(GraphQLInt)
    }

fun DataFetchingEnvironment.getDurationArgument() =
    getArgument<Int>(ARG_DURATION)?.let { seconds ->
        Duration.ofSeconds(seconds.toLong())
    }

fun DefinitionBuilder.rateArgument(): DefinitionBuilder =
    argument {
        it.name(ARG_RATE)
            .description("Rate must be worse or equal.")
            .type(GraphQLString)
    }

fun DataFetchingEnvironment.getRateArgument() =
    getArgument<String>(ARG_RATE)?.let {
        Rating.valueOf(it)
    }
