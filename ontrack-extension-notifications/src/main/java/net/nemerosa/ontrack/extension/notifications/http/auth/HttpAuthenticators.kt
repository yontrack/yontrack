package net.nemerosa.ontrack.extension.notifications.http.auth

/**
 * The authentication types of the outgoing HTTP calls: `none`, `basic`, `bearer` and `header`.
 *
 * They are stateless and fixed, so that a configuration holding an [HttpAuthentication] can
 * obfuscate and merge it by itself.
 */
object HttpAuthenticators {

    private val index: Map<String, HttpAuthenticator<*>> = listOf(
        NoneHttpAuthenticator,
        BasicHttpAuthenticator,
        BearerHttpAuthenticator,
        HeaderHttpAuthenticator,
    ).associateBy { it.type }

    /**
     * All the authenticators, sorted by type
     */
    val authenticators: List<HttpAuthenticator<*>> = index.values.sortedBy { it.type }

    fun findAuthenticator(type: String): HttpAuthenticator<*>? = index[type]

    fun getAuthenticator(type: String): HttpAuthenticator<*> =
        findAuthenticator(type) ?: throw HttpAuthenticatorNotFoundException(type)

}
