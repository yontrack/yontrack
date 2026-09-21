package net.nemerosa.ontrack.extension.notifications.webhooks.auth

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.annotations.APILabel
import org.springframework.stereotype.Component
import java.net.http.HttpRequest
import java.util.*

@Component
class BasicWebhookAuthenticator : AbstractWebhookAuthenticator<BasicWebhookAuthenticatorConfig>() {

    override val type: String = "basic"

    override val displayName: String = "Basic authentication"

    override fun validateConfig(node: JsonNode): BasicWebhookAuthenticatorConfig = node.parse()

    override fun authenticate(config: BasicWebhookAuthenticatorConfig, builder: HttpRequest.Builder) {
        val encoded = Base64.getEncoder().encodeToString(
            "${config.username}:${config.password}".toByteArray()
        )
        builder.header("Authorization", "Basic $encoded")
    }

    override fun obfuscate(config: BasicWebhookAuthenticatorConfig) = config.obfuscate()

    override fun merge(
        input: BasicWebhookAuthenticatorConfig,
        existing: BasicWebhookAuthenticatorConfig
    ) = BasicWebhookAuthenticatorConfig(
        username = input.username,
        password = merge(input.password, existing.password),
    )
}

data class BasicWebhookAuthenticatorConfig(
    @APILabel("Username")
    @APIDescription("Username used to connect to the webhook")
    val username: String,
    @APILabel("Password")
    @APIDescription("Password used to connect to the webhook")
    val password: String = "",
) {
    fun obfuscate() = BasicWebhookAuthenticatorConfig(
        username = username,
        password = "",
    )
}
