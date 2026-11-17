package net.nemerosa.ontrack.kdsl.spec.extension.notifications.http

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Configuration of a subscription with the `http` channel, to pass as `channelConfig`.
 *
 * @property config Name of the HTTP configuration
 * @property method `GET`, `POST` or `PUT`
 * @property path Path under the base URL of the configuration, templated
 * @property headers Extra headers, values templated
 * @property body JSON body, for `POST` and `PUT` only, string values templated
 * @property successCondition CEL condition telling that the call succeeded
 * @property failureCondition CEL condition telling that the call failed
 * @property statusUrl CEL expression giving the URL to poll, for `POST` and `PUT`
 * @property intervalSeconds Interval between two polls, 5 at least
 * @property timeoutSeconds Maximum time to wait for a condition to hold
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HttpNotificationChannelConfig(
    val config: String,
    val method: String = GET,
    val path: String = "",
    val headers: List<Header> = emptyList(),
    val body: Any? = null,
    val successCondition: String? = null,
    val failureCondition: String? = null,
    val statusUrl: String? = null,
    val intervalSeconds: Int? = null,
    val timeoutSeconds: Int? = null,
) {
    data class Header(
        val name: String,
        val value: String,
    )

    companion object {
        const val CHANNEL = "http"
        const val GET = "GET"
        const val POST = "POST"
        const val PUT = "PUT"
    }
}
