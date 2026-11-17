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
)

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
