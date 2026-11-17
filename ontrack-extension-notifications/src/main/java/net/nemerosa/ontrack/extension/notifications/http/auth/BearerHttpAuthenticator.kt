package net.nemerosa.ontrack.extension.notifications.http.auth

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.annotations.APILabel
import tools.jackson.databind.JsonNode

object BearerHttpAuthenticator : AbstractHttpAuthenticator<BearerHttpAuthenticatorConfig>() {

    override val type: String = "bearer"

    override val displayName: String = "Bearer token authentication"

    override fun validateConfig(node: JsonNode): BearerHttpAuthenticatorConfig = node.parse()

    override fun authenticate(config: BearerHttpAuthenticatorConfig, header: (name: String, value: String) -> Unit) {
        header("Authorization", "Bearer ${config.token}")
    }

    override fun obfuscate(config: BearerHttpAuthenticatorConfig) = config.obfuscate()

    override fun merge(
        input: BearerHttpAuthenticatorConfig,
        existing: BearerHttpAuthenticatorConfig
    ) = BearerHttpAuthenticatorConfig(
        token = merge(input.token, existing.token),
    )
}

data class BearerHttpAuthenticatorConfig(
    @APILabel("Token")
    @APIDescription("Token used to authenticate")
    val token: String = "",
) {
    fun obfuscate() = BearerHttpAuthenticatorConfig(
        token = "",
    )
}
