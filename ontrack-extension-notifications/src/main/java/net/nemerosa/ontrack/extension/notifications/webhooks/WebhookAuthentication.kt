package net.nemerosa.ontrack.extension.notifications.webhooks

import tools.jackson.databind.JsonNode

data class WebhookAuthentication(
    val type: String,
    val config: JsonNode,
)