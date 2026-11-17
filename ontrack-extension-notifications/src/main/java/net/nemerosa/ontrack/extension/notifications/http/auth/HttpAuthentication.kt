package net.nemerosa.ontrack.extension.notifications.http.auth

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory

/**
 * Authentication of an outgoing HTTP call: its type, one of [HttpAuthenticators], and its
 * configuration, whose shape depends on the type.
 */
data class HttpAuthentication(
    @APIDescription("Authentication type: none, basic, bearer or header")
    val type: String,
    @APIDescription("Authentication configuration (JSON)")
    val config: JsonNode = JsonNodeFactory.instance.objectNode(),
) {

    /**
     * Checks the type is known and the configuration can be parsed.
     *
     * @throws HttpAuthenticatorNotFoundException If the type is not known
     * @throws HttpAuthenticationConfigException If the configuration is invalid
     */
    fun validate() {
        val authenticator = HttpAuthenticators.getAuthenticator(type)
        try {
            authenticator.validateConfig(config)
        } catch (ex: JsonParseException) {
            throw HttpAuthenticationConfigException(type, ex)
        }
    }

    /**
     * Gets the headers authenticating a request
     */
    fun headers(): Map<String, String> = headers(HttpAuthenticators.getAuthenticator(type))

    private fun <C> headers(authenticator: HttpAuthenticator<C>): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        authenticator.authenticate(authenticator.validateConfig(config)) { name, value ->
            headers[name] = value
        }
        return headers.toMap()
    }

    /**
     * Copy of this authentication without its secrets. A configuration which cannot be read
     * (unknown type, invalid or still encrypted content) is dropped altogether.
     */
    fun obfuscate(): HttpAuthentication {
        val authenticator = HttpAuthenticators.findAuthenticator(type)
        val obfuscatedConfig = authenticator?.let {
            try {
                obfuscate(it)
            } catch (_: JsonParseException) {
                null
            }
        } ?: JsonNodeFactory.instance.objectNode()
        return HttpAuthentication(type = type, config = obfuscatedConfig)
    }

    private fun <C> obfuscate(authenticator: HttpAuthenticator<C>): JsonNode =
        authenticator.obfuscate(authenticator.validateConfig(config)).asJson()

    /**
     * Merges this authentication, as input, with an existing one: for the same type, a blank
     * secret keeps the existing one. Otherwise, this authentication is returned as it is.
     */
    fun merge(existing: HttpAuthentication): HttpAuthentication =
        if (type == existing.type) {
            val authenticator = HttpAuthenticators.getAuthenticator(type)
            HttpAuthentication(
                type = type,
                config = merge(authenticator, existing.config),
            )
        } else {
            this
        }

    private fun <C> merge(authenticator: HttpAuthenticator<C>, existingConfig: JsonNode): JsonNode =
        authenticator.merge(
            input = authenticator.validateConfig(config),
            existing = authenticator.validateConfig(existingConfig),
        ).asJson()

    companion object {
        /**
         * No authentication
         */
        fun none() = HttpAuthentication(type = NoneHttpAuthenticator.TYPE)
    }
}
