package net.nemerosa.ontrack.json

import tools.jackson.databind.JsonNode

object JsonPathUtils {

    fun get(root: JsonNode, path: String): JsonNode? =
        if (path.isBlank()) {
            null
        } else if (path == ".") {
            root
        } else {
            path.split('.').fold(root) { current, key ->
                current.get(key) ?: return null
            }
        }

}