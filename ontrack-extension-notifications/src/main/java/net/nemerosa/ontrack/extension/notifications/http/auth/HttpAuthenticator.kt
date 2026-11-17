package net.nemerosa.ontrack.extension.notifications.http.auth

import net.nemerosa.ontrack.common.api.APIDescription
import tools.jackson.databind.JsonNode

/**
 * Way to authenticate an outgoing HTTP request, shared by the webhooks and the HTTP configurations.
 *
 * @param C Type of the configuration of this authentication
 */
interface HttpAuthenticator<C> {

    @APIDescription("Authentication type")
    val type: String

    @APIDescription("Authentication display name")
    val displayName: String

    /**
     * Parses the JSON configuration into its typed version.
     *
     * @throws net.nemerosa.ontrack.json.JsonParseException If the configuration cannot be parsed
     */
    fun validateConfig(node: JsonNode): C

    /**
     * Sets the headers which authenticate a request.
     *
     * @param config Configuration of the authentication
     * @param header Callback for each header to set
     */
    fun authenticate(config: C, header: (name: String, value: String) -> Unit)

    /**
     * Erases the secrets from a configuration
     */
    fun obfuscate(config: C): C

    /**
     * Merges a new configuration with an existing one, keeping the existing secret when
     * the new one is blank.
     */
    fun merge(input: C, existing: C): C

}
