package net.nemerosa.ontrack.graphql.support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.*
import graphql.language.*
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.json.ObjectMapperFactory
import net.nemerosa.ontrack.json.asJson


/**
 * JSON scalar type.
 */
object GQLScalarJSON {

    /**
     * Name of the GraphQL type
     */
    const val JSON = "JSON"

    @JvmField
    val INSTANCE = GraphQLTypeReference(JSON)

    /**
     * If a field contains one of these tokens (in lowercase), it's not exposed
     */
    private val forbiddenFields = setOf(
        "password",
        "token",
        "privatekey",
    )

    /**
     * The actual type to register
     */
    val TYPE: GraphQLScalarType = GraphQLScalarType.newScalar()
        .name(JSON)
        .description("JSON node")
        .coercing(
            object : Coercing<JsonNode, JsonNode> {

                private val mapper = ObjectMapperFactory.create()

                override fun serialize(dataFetcherResult: Any): JsonNode {
                    val json = when (dataFetcherResult) {
                        is JsonNode -> dataFetcherResult
                        else -> mapper.valueToTree(dataFetcherResult)
                    }
                    return json.obfuscate()
                }

                override fun parseValue(input: Any): JsonNode =
                    when (input) {
                        is String -> try {
                            mapper.readTree(input)
                        } catch (_: Exception) {
                            TextNode(input)
                        }
                        is JsonNode -> input
                        else -> input.asJson()
                    }

                override fun parseLiteral(input: Any): JsonNode =
                    when (input) {
                        is NullValue -> NullNode.instance
                        is FloatValue -> DoubleNode.valueOf(input.value.toDouble())
                        is StringValue -> TextNode.valueOf(input.value)
                        is IntValue -> IntNode.valueOf(input.value.toInt())
                        is BooleanValue -> BooleanNode.valueOf(input.isValue)
                        is ArrayValue -> {
                            val array = ArrayNode(mapper.nodeFactory)
                            input.values.forEach { value ->
                                array.add(parseLiteral(value))
                            }
                            array
                        }

                        is ObjectValue -> {
                            val `object` = ObjectNode(mapper.nodeFactory)
                            input.objectFields.forEach { of ->
                                `object`.set<JsonNode>(of.name, parseLiteral(of.value))
                            }
                            `object`
                        }

                        else -> throw CoercingParseValueException("Cannot parse value for ${input::class}")
                    }

                private fun JsonNode.obfuscate(): JsonNode {
                    val factory: JsonNodeFactory = mapper.nodeFactory
                    return when (this) {
                        // Transforms each element
                        is ArrayNode ->
                            ArrayNode(
                                factory,
                                map { it.obfuscate() }
                            )
                        // Filters at field level
                        is ObjectNode ->
                            ObjectNode(
                                factory,
                                fields().asSequence()
                                    .filter { (name, value) ->
                                        if (value is TextNode || value is NullNode) {
                                            // Keeping the field only if name not containing any forbidden token
                                            forbiddenFields.none { part ->
                                                name.contains(part, ignoreCase = true)
                                            }
                                        } else {
                                            true // Keeping
                                        }
                                    }
                                    .associate { (name: String, value: JsonNode?) ->
                                        name to value?.obfuscate()
                                    }
                            )
                        // No transformation for an end element
                        else -> this
                    }
                }
            }
        )
        .build()
}
