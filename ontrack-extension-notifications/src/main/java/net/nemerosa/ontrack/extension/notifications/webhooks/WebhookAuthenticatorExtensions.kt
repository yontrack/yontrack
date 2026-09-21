package net.nemerosa.ontrack.extension.notifications.webhooks

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson

fun <C> WebhookAuthenticator<C>.obfuscateJson(config: JsonNode): JsonNode {
    val parsedConfig = validateConfig(config)
    val obfuscated = obfuscate(parsedConfig)
    return obfuscated.asJson()
}

fun <C> WebhookAuthenticator<C>.merge(input: JsonNode, existing: JsonNode): C {
    val inputConfig = validateConfig(input)
    val existingConfig = validateConfig(existing)
    return merge(input = inputConfig, existing = existingConfig)
}