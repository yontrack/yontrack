package net.nemerosa.ontrack.extension.notifications.webhooks

import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthenticator

/**
 * The authentication types a webhook accepts: those of the HTTP calls, but `none`.
 */
interface WebhookAuthenticatorRegistry {

    /**
     * List of available authenticators
     */
    val authenticators: List<HttpAuthenticator<*>>

    fun findWebhookAuthenticator(type: String): HttpAuthenticator<*>?

}
