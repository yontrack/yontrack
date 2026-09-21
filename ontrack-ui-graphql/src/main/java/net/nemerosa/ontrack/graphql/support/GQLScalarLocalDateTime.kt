package net.nemerosa.ontrack.graphql.support

import graphql.language.StringValue
import graphql.schema.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * JSON scalar type.
 */
object GQLScalarLocalDateTime {

    val INSTANCE: GraphQLScalarType = GraphQLScalarType.newScalar()
            .name("LocalDateTime")
            .description("Local Date Time")
            .coercing(
                    object : Coercing<LocalDateTime, String?> {

                        override fun serialize(dataFetcherResult: Any): String? =
                                if (dataFetcherResult is LocalDateTime) {
                                    dataFetcherResult.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                } else {
                                    throw CoercingSerializeException("Cannot serialize ${dataFetcherResult::class.java} into a string")
                                }

                        override fun parseValue(input: Any): LocalDateTime =
                                when (input) {
                                    is String -> try {
                                        parse(input)
                                    } catch (ex: DateTimeParseException) {
                                        throw CoercingParseValueException("Cannot parse value: $input", ex)
                                    }

                                    is LocalDateTime -> input
                                    else -> throw CoercingParseValueException("Cannot parse value: $input")
                                }

                        override fun parseLiteral(input: Any): LocalDateTime =
                                when (input) {
                                    is StringValue -> try {
                                        parse(input.value ?: throw CoercingParseLiteralException("Cannot parse literal: $input"))
                                    } catch (ex: DateTimeParseException) {
                                        throw CoercingParseLiteralException("Cannot parse literal: $input", ex)
                                    }

                                    else -> throw CoercingParseLiteralException("Cannot parse literal: $input")
                                }

                        private fun parse(input: String): LocalDateTime =
                                // Tries first with local date time
                                try {
                                    LocalDateTime.parse(input, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                } catch (ignored: DateTimeParseException) {
                                    // ... with a time zone
                                    LocalDateTime.ofInstant(Instant.parse(input), ZoneOffset.UTC)
                                }
                    }
            )
            .build()

}