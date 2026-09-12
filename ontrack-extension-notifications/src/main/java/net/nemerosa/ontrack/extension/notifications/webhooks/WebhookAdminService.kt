package net.nemerosa.ontrack.extension.notifications.webhooks

import java.time.Duration

interface WebhookAdminService {

    /**
     * Gets the list of all webhooks
     */
    val webhooks: List<Webhook>

    /**
     * Gets a webhook using its name, or null if it does not exist.
     *
     * Requires the [WebhookManagement] global function: the returned [Webhook] carries the URL and the
     * authentication configured by an administrator, and merely confirming a name would let any
     * authenticated user probe them. Internal callers which legitimately need the webhook on behalf of
     * a non-administrator - the notification channel, for one - must wrap the call in
     * `securityService.asAdmin { ... }`, and must not hand the [Webhook] back to that caller.
     */
    fun findWebhookByName(name: String): Webhook?

    fun createWebhook(
        name: String,
        enabled: Boolean,
        url: String,
        timeout: Duration,
        authentication: WebhookAuthentication,
    ): Webhook

    fun updateWebhook(
        name: String,
        enabled: Boolean? = null,
        url: String? = null,
        timeout: Duration? = null,
        authentication: WebhookAuthentication? = null,
    ): Webhook

    fun deleteWebhook(name: String)

}