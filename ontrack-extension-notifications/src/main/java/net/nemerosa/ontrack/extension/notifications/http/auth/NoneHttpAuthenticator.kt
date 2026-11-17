package net.nemerosa.ontrack.extension.notifications.http.auth

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory

/**
 * No authentication at all, for an anonymous API. Its configuration is always empty.
 */
object NoneHttpAuthenticator : HttpAuthenticator<JsonNode> {

    const val TYPE = "none"

    override val type: String = TYPE

    override val displayName: String = "No authentication"

    override fun validateConfig(node: JsonNode): JsonNode = JsonNodeFactory.instance.objectNode()

    override fun authenticate(config: JsonNode, header: (name: String, value: String) -> Unit) {
    }

    override fun obfuscate(config: JsonNode): JsonNode = config

    override fun merge(input: JsonNode, existing: JsonNode): JsonNode = input
}
