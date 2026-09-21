package net.nemerosa.ontrack.graphql.support

import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.*
import java.math.BigInteger

/**
 * Long scalar type.
 */
object GQLScalarLong {
    val INSTANCE: GraphQLScalarType = GraphQLScalarType.newScalar()
            .name("Long")
            .description("Long signed integer")
            .coercing(
                    object : Coercing<Long, IntValue> {

                        override fun serialize(dataFetcherResult: Any): IntValue =
                                if (dataFetcherResult is Long) {
                                    IntValue(BigInteger.valueOf(dataFetcherResult))
                                } else {
                                    throw CoercingSerializeException("Cannot serialize ${dataFetcherResult::class.java} into a string")
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