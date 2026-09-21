package net.nemerosa.ontrack.extension.notifications.webhooks.auth

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.annotations.APILabel
import org.springframework.stereotype.Component
import java.net.http.HttpRequest

@Component
class HeaderWebhookAuthenticator : AbstractWebhookAuthenticator<HeaderWebhookAuthenticatorConfig>() {

    override val type: String = "header"

    override val displayName: String = "HTTP Header authentication"

    override fun validateConfig(node: JsonNode): HeaderWebhookAuthenticatorConfig = node.parse()

    override fun authenticate(config: HeaderWebhookAuthenticatorConfig, builder: HttpRequest.Builder) {
        builder.header(config.name, config.value)
    }

    override fun obfuscate(config: HeaderWebhookAuthenticatorConfig) = config.obfuscate()

    override fun merge(
        input: HeaderWebhookAuthenticatorConfig,
        existing: HeaderWebhookAuthenticatorConfig
    ) = HeaderWebhookAuthenticatorConfig(
        name = input.name,
        value = merge(input.value, existing.value),
    )
}

data class HeaderWebhookAuthenticatorConfig(
    @APILabel("Header name")
    @APIDescription("Name of the header to send to the webhook")
    val name: String,
    @APILabel("Header value")
    @APIDescription("Value of the header to send to the webhook")
    val value: String = "",
) {
    fun obfuscate() = HeaderWebhookAuthenticatorConfig(
        name = name,
        value = ""
    )
}
