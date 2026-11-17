package net.nemerosa.ontrack.extension.notifications.webhooks

import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthentication

/**
 * A webhook is authenticated the way any HTTP call is, with the types of [HttpAuthentication]
 * but `none`.
 */
typealias WebhookAuthentication = HttpAuthentication
