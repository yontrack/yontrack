package net.nemerosa.ontrack.extension.notifications.http.config

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthentication
import net.nemerosa.ontrack.json.format
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.support.CredentialsConfiguration
import tools.jackson.databind.node.StringNode
import java.net.URI
import java.net.URISyntaxException

/**
 * HTTP configuration: a base URL and the authentication of the HTTP calls made under it.
 *
 * The set of HTTP configurations is the allowlist of the hosts Yontrack may call.
 *
 * @property name Unique name of the configuration
 * @property url Base URL
 * @property authentication Authentication of the calls
 */
@APIDescription("HTTP configuration: a base URL and an authentication")
data class HttpConfiguration(
    @APIDescription("Unique name for the configuration")
    @APILabel("Name")
    override val name: String,
    @APIDescription("Base URL: every call made through this configuration stays under it")
    @APILabel("Base URL")
    val url: String,
    @APIDescription("Authentication of the calls: type (none, basic, bearer, header) and configuration")
    @APILabel("Authentication")
    val authentication: HttpAuthentication = HttpAuthentication.none(),
) : CredentialsConfiguration<HttpConfiguration> {

    /**
     * Checks the base URL and the authentication.
     *
     * @throws HttpConfigurationUrlException If the base URL is not valid
     * @throws net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthenticatorNotFoundException If the authentication type is unknown
     * @throws net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthenticationConfigException If the authentication configuration is invalid
     */
    fun check() {
        checkUrl(url)
        authentication.validate()
    }

    override fun obfuscate() = copy(
        authentication = authentication.obfuscate(),
    )

    override fun injectCredentials(oldConfig: HttpConfiguration) = copy(
        authentication = authentication.merge(oldConfig.authentication),
    )

    /**
     * The whole authentication configuration is encrypted, as one JSON string.
     */
    override fun encrypt(crypting: (plain: String?) -> String?) = copy(
        authentication = authentication.copy(
            config = StringNode.valueOf(crypting(authentication.config.format()))
        )
    )

    override fun decrypt(decrypting: (encrypted: String?) -> String?): HttpConfiguration {
        val config = authentication.config
        return if (config.isString) {
            copy(
                authentication = authentication.copy(
                    config = decrypting(config.asString())?.parseAsJson()
                        ?: HttpAuthentication.none().config
                )
            )
        } else {
            this
        }
    }

    companion object {

        private val schemes = setOf("http", "https")

        fun checkUrl(url: String) {
            val uri = try {
                URI(url)
            } catch (_: URISyntaxException) {
                throw HttpConfigurationUrlException(url, "it is not a valid URL")
            }
            if (!uri.isAbsolute || uri.scheme.lowercase() !in schemes) {
                throw HttpConfigurationUrlException(url, "it must be an absolute http or https URL")
            }
            if (uri.host.isNullOrBlank()) {
                throw HttpConfigurationUrlException(url, "it must have a host")
            }
            if (uri.rawUserInfo != null) {
                throw HttpConfigurationUrlException(url, "credentials go in the authentication, not in the URL")
            }
            if (uri.rawQuery != null || uri.rawFragment != null) {
                throw HttpConfigurationUrlException(url, "it must have neither a query nor a fragment")
            }
        }
    }
}
