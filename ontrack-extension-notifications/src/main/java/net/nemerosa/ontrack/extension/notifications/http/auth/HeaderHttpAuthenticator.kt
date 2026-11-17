package net.nemerosa.ontrack.extension.notifications.http.auth

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.annotations.APILabel
import tools.jackson.databind.JsonNode

object HeaderHttpAuthenticator : AbstractHttpAuthenticator<HeaderHttpAuthenticatorConfig>() {

    override val type: String = "header"

    override val displayName: String = "HTTP Header authentication"

    override fun validateConfig(node: JsonNode): HeaderHttpAuthenticatorConfig = node.parse()

    override fun authenticate(config: HeaderHttpAuthenticatorConfig, header: (name: String, value: String) -> Unit) {
        header(config.name, config.value)
    }

    override fun obfuscate(config: HeaderHttpAuthenticatorConfig) = config.obfuscate()

    override fun merge(
        input: HeaderHttpAuthenticatorConfig,
        existing: HeaderHttpAuthenticatorConfig
    ) = HeaderHttpAuthenticatorConfig(
        name = input.name,
        value = merge(input.value, existing.value),
    )
}

data class HeaderHttpAuthenticatorConfig(
    @APILabel("Header name")
    @APIDescription("Name of the header to send")
    val name: String,
    @APILabel("Header value")
    @APIDescription("Value of the header to send")
    val value: String = "",
) {
    fun obfuscate() = HeaderHttpAuthenticatorConfig(
        name = name,
        value = ""
    )
}
