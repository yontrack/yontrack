package net.nemerosa.ontrack.kdsl.spec.extension.notifications.http

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import net.nemerosa.ontrack.kdsl.spec.Configuration

/**
 * HTTP configuration: the base URL and the authentication of the calls of the `http` channel.
 *
 * @property name Unique name of the configuration
 * @property url Base URL
 * @property authentication Authentication of the calls
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HttpConfiguration(
    override val name: String,
    val url: String,
    val authentication: HttpAuthentication = HttpAuthentication.none(),
) : Configuration

/**
 * Authentication of an HTTP configuration.
 *
 * @property type `none`, `basic`, `bearer` or `header`
 * @property config Configuration of the type
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class HttpAuthentication(
    val type: String,
    val config: Map<String, String> = emptyMap(),
) {
    companion object {
        fun none() = HttpAuthentication(type = "none")

        fun basic(username: String, password: String) = HttpAuthentication(
            type = "basic",
            config = mapOf("username" to username, "password" to password),
        )

        fun bearer(token: String) = HttpAuthentication(
            type = "bearer",
            config = mapOf("token" to token),
        )

        fun header(name: String, value: String) = HttpAuthentication(
            type = "header",
            config = mapOf("name" to name, "value" to value),
        )
    }
}
