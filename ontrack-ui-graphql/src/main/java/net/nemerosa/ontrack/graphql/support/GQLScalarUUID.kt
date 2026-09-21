package net.nemerosa.ontrack.graphql.support

import graphql.language.StringValue
import graphql.schema.*
import java.util.*

/**
 * UUID scalar type.
 */
object GQLScalarUUID {
    val INSTANCE: GraphQLScalarType = GraphQLScalarType.newScalar()
            .name("UUID")
            .description("UUID")
            .coercing(
                    object : Coercing<UUID, String?> {

                        override fun serialize(dataFetcherResult: Any): String =
                                if (dataFetcherResult is UUID) {
                                    dataFetcherResult.toString()
                                } else {
                                    throw CoercingSerializeException("Cannot serialize ${dataFetcherResult::class.java} into a string")
                                }

                        override fun parseValue(input: Any): UUID =
                                when (input) {
                                    is String -> try {
                                        parse(input)
                                    } catch (ex: IllegalArgumentException) {
                                        throw CoercingParseValueException("Cannot parse value: $input", ex)
                                    }

                                    else -> throw CoercingParseValueException("Cannot parse value: $input")
                                }

                        override fun parseLiteral(input: Any): UUID =
                                when (input) {
                                    is StringValue -> try {
                                        parse(input.value ?: throw CoercingParseLiteralException("Cannot parse literal: $input"))
                                    } catch (ex: IllegalArgumentException) {
                                        throw CoercingParseLiteralException("Cannot parse literal: $input", ex)
                                    }

                                    else -> throw CoercingParseLiteralException("Cannot parse literal: $input")
                                }

                        private fun parse(input: String): UUID =
                                UUID.fromString(input)
                    }
            )
            .build()
}