package net.nemerosa.ontrack.graphql.support

import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.*

/**
 * Long scalar type.
 *
 * Output as a JSON number: the serialized value is the `Long` itself, never a literal of the
 * GraphQL language, which JSON would render as an object.
 */
object GQLScalarLong {
    val INSTANCE: GraphQLScalarType = GraphQLScalarType.newScalar()
            .name("Long")
            .description("Long signed integer")
            .coercing(
                    object : Coercing<Long, Long> {

                        override fun serialize(dataFetcherResult: Any): Long =
                                when (dataFetcherResult) {
                                    is Long -> dataFetcherResult
                                    is Int -> dataFetcherResult.toLong()
                                    else -> throw CoercingSerializeException("Cannot serialize ${dataFetcherResult::class.java} into a long")
                                }

                        override fun parseValue(input: Any): Long =
                                when (input) {
                                    is String -> try {
                                        parse(input)
                                    } catch (ex: IllegalArgumentException) {
                                        throw CoercingParseValueException("Cannot parse value: $input", ex)
                                    }

                                    is Number -> input.toLong()
                                    else -> throw CoercingParseValueException("Cannot parse value: $input")
                                }

                        override fun parseLiteral(input: Any): Long =
                                when (input) {
                                    is StringValue -> try {
                                        parse(input.value ?: throw CoercingParseLiteralException("Cannot parse literal: $input"))
                                    } catch (ex: IllegalArgumentException) {
                                        throw CoercingParseLiteralException("Cannot parse literal: $input", ex)
                                    }

                                    is IntValue -> input.value.toLong()
                                    else -> throw CoercingParseLiteralException("Cannot parse literal: $input")
                                }

                        private fun parse(input: String): Long = input.toLong()
                    }
            )
            .build()
}