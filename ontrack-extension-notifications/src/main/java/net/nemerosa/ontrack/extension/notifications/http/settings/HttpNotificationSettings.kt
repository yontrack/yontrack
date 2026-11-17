package net.nemerosa.ontrack.extension.notifications.http.settings

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel

/**
 * Global settings of the HTTP call notifications.
 *
 * There is no `enabled` flag: without any HTTP configuration, no call is possible.
 */
@APIDescription("Settings of the HTTP call notifications")
data class HttpNotificationSettings(
    @APIDescription("Maximum timeout (in seconds) a subscription may set on an HTTP call")
    @APILabel("Maximum timeout (s)")
    val maxTimeoutSeconds: Int = DEFAULT_MAX_TIMEOUT_SECONDS,
    @APIDescription("Maximum size (in bytes) of a response body: a longer body is truncated")
    @APILabel("Maximum response size (bytes)")
    val maxResponseBytes: Int = DEFAULT_MAX_RESPONSE_BYTES,
    @APIDescription("Timeout (in seconds) of a single request, for both the connection and the reading of the response")
    @APILabel("Request timeout (s)")
    val requestTimeoutSeconds: Int = DEFAULT_REQUEST_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_MAX_TIMEOUT_SECONDS = 3600
        const val DEFAULT_MAX_RESPONSE_BYTES = 65536
        const val DEFAULT_REQUEST_TIMEOUT_SECONDS = 30
    }
}
