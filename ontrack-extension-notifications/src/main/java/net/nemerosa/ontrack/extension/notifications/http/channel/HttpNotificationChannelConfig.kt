package net.nemerosa.ontrack.extension.notifications.http.channel

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.docs.DocumentationList
import tools.jackson.databind.JsonNode

/**
 * Configuration of the `http` channel: one call under an HTTP configuration.
 *
 * @property config Name of the HTTP configuration
 * @property method Verb of the call
 * @property path Templated path, with an optional query, appended to the base URL of the configuration
 * @property headers Extra headers, with templated values
 * @property body JSON body, for `POST` and `PUT` only, whose string leaves are templated
 * @property successCondition CEL condition on a response telling that the call succeeded
 * @property failureCondition CEL condition on a response telling that the call failed
 * @property statusUrl CEL expression giving, from the initial response of a `POST` or `PUT`, the path to poll
 * @property intervalSeconds Interval between two polls
 * @property timeoutSeconds Maximum time to wait for a condition to hold
 */
data class HttpNotificationChannelConfig(
    @APIDescription("Name of the HTTP configuration: its base URL and its authentication.")
    @APILabel("Configuration")
    val config: String,
    @APIDescription("Verb of the call: GET, POST or PUT.")
    @APILabel("Method")
    val method: HttpCallMethod = HttpCallMethod.GET,
    @APIDescription("Path of the call, with an optional query, appended to the base URL of the HTTP configuration. Templated. After templating, it must stay under the base URL: an absolute URL, or a `.` or `..` segment, is refused.")
    @APILabel("Path")
    val path: String = "",
    @APIDescription("Extra headers of the call. Their values are templated. Authorization, and any header set by the HTTP configuration, are refused.")
    @APILabel("Headers")
    @DocumentationList
    val headers: List<HttpNotificationChannelConfigHeader> = emptyList(),
    @APIDescription("JSON body of the call, for POST and PUT only, sent as application/json. Its string values are templated; numbers and booleans are sent as they are. No body is sent when it is absent.")
    @APILabel("Body")
    val body: JsonNode? = null,
    @APIDescription("CEL condition on a response, telling that the call succeeded, for example `response.body.status == 'SUCCEEDED'`. It sees `response.status`, `response.headers` (by lowercase name, first value), `response.body` (parsed JSON) and `attempt`. Without it, any 2xx response is a success. With it, the call is awaited: a GET is polled on its own URL, a POST or PUT on its status URL, until a condition holds or the timeout expires.")
    @APILabel("Success condition")
    val successCondition: String? = null,
    @APIDescription("CEL condition on a response, telling that the call failed, for example `response.body.status == 'FAILED'`. Requires a success condition. When both hold, the call fails.")
    @APILabel("Failure condition")
    val failureCondition: String? = null,
    @APIDescription("CEL expression evaluated on the initial response of a POST or PUT, giving the path to poll with GET, for example `response.headers['location']` or `'/deployments/' + response.body.id`. It is resolved under the base URL of the HTTP configuration, like the path. Requires a success condition. Without it, the conditions of a POST or PUT are evaluated on the initial response only.")
    @APILabel("Status URL")
    val statusUrl: String? = null,
    @APIDescription("Interval between two polls, in seconds. At least 5.")
    @APILabel("Interval (s)")
    val intervalSeconds: Int = DEFAULT_INTERVAL_SECONDS,
    @APIDescription("Maximum time to wait for a condition to hold, in seconds, capped by the maximum timeout of the HTTP notifications settings. At least the interval.")
    @APILabel("Timeout (s)")
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_INTERVAL_SECONDS = 10
        const val MIN_INTERVAL_SECONDS = 5
        const val DEFAULT_TIMEOUT_SECONDS = 300
    }
}

/**
 * Extra header of an HTTP call.
 */
data class HttpNotificationChannelConfigHeader(
    @APIDescription("Name of the header")
    @APILabel("Name")
    val name: String,
    @APIDescription("Value of the header. Templated.")
    @APILabel("Value")
    val value: String,
)
