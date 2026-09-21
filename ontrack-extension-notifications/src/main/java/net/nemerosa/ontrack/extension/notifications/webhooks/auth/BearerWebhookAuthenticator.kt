package net.nemerosa.ontrack.extension.notifications.webhooks.auth

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.annotations.APILabel
import org.springframework.stereotype.Component
import java.net.http.HttpRequest

@Component
class BearerWebhookAuthenticator : AbstractWebhookAuthenticator<BearerWebhookAuthenticatorConfig>() {

    override val type: String = "bearer"

    override val displayName: String = "Bearer token authentication"

    override fun validateConfig(node: JsonNode): BearerWebhookAuthenticatorConfig = node.parse()

    override fun authenticate(config: BearerWebhookAuthenticatorConfig, builder: HttpRequest.Builder) {
        builder.header("Authorization", "Bearer ${config.token}")
    }

    override fun obfuscate(config: BearerWebhookAuthenticatorConfig) = config.obfuscate()

    override fun merge(
        input: BearerWebhookAuthenticatorConfig,
        existing: BearerWebhookAuthenticatorConfig
    ) = BearerWebhookAuthenticatorConfig(
        token = merge(input.token, existing.token),
    )
}

data class BearerWebhookAuthenticatorConfig(
    @APILabel("Token")
    @APIDescription("Token used to connect to the webhook")
    val token: String = "",
) {
    fun obfuscate() = BearerWebhookAuthenticatorConfig(
        token = "",
    )
}
