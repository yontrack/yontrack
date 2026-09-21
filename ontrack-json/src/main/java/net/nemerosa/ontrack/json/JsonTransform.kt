package net.nemerosa.ontrack.json

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.StringNode

fun JsonNode.transform(textTransform: (text: String) -> String): JsonNode =
    when {

        isTextual -> StringNode(textTransform(asText()))

        isArray -> values().map {
            it.transform(textTransform)
        }.asJson()

        isObject -> properties().asSequence().map { (name, value) ->
            name to value.transform(textTransform)
        }.toMap().asJson()

        else -> this
    }
