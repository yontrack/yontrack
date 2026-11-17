package net.nemerosa.ontrack.extension.notifications.http.auth

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.annotations.APILabel
import tools.jackson.databind.JsonNode
import java.util.*

object BasicHttpAuthenticator : AbstractHttpAuthenticator<BasicHttpAuthenticatorConfig>() {

    override val type: String = "basic"

    override val displayName: String = "Basic authentication"

    override fun validateConfig(node: JsonNode): BasicHttpAuthenticatorConfig = node.parse()

    override fun authenticate(config: BasicHttpAuthenticatorConfig, header: (name: String, value: String) -> Unit) {
        val encoded = Base64.getEncoder().encodeToString(
            "${config.username}:${config.password}".toByteArray()
        )
        header("Authorization", "Basic $encoded")
    }

    override fun obfuscate(config: BasicHttpAuthenticatorConfig) = config.obfuscate()

    override fun merge(
        input: BasicHttpAuthenticatorConfig,
        existing: BasicHttpAuthenticatorConfig
    ) = BasicHttpAuthenticatorConfig(
        username = input.username,
        password = merge(input.password, existing.password),
    )
}

data class BasicHttpAuthenticatorConfig(
    @APILabel("Username")
    @APIDescription("Username used to authenticate")
    val username: String,
    @APILabel("Password")
    @APIDescription("Password used to authenticate")
    val password: String = "",
) {
    fun obfuscate() = BasicHttpAuthenticatorConfig(
        username = username,
        password = "",
    )
}
