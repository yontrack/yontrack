package net.nemerosa.ontrack.extension.notifications.http.channel

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel
import net.nemerosa.ontrack.model.docs.DocumentationType
import tools.jackson.databind.JsonNode

/**
 * What the `http` channel records about a call.
 *
 * It never holds a secret: the authentication of the HTTP configuration is not recorded, and the
 * `Authorization` and `Set-Cookie` response headers, as well as any response header named like one set
 * by the configuration, are left out.
 */
data class HttpNotificationChannelOutput(
    @APIDescription("Verb of the call")
    @APILabel("Method")
    val method: HttpCallMethod,
    @APIDescription("URL which was called, after templating")
    @APILabel("URL")
    val url: String,
    @APIDescription("Status code of the response, absent when no response was received")
    @APILabel("Status")
    val status: Int? = null,
    @APIDescription("Headers of the response, by lowercase name, repeated values joined by a comma. Authorization, Set-Cookie and the headers set by the HTTP configuration are left out.")
    @APILabel("Headers")
    @DocumentationType("Map")
    val headers: Map<String, String> = emptyMap(),
    @APIDescription("Body of the response: parsed when it is JSON, as text otherwise. It is read up to the maximum response size of the HTTP notifications settings.")
    @APILabel("Body")
    val body: JsonNode? = null,
    @APIDescription("True when the body of the response was longer than the maximum response size, and was cut")
    @APILabel("Truncated")
    val truncated: Boolean = false,
    @APIDescription("Number of requests sent: the initial one, and the polls")
    @APILabel("Attempts")
    val attempts: Int = 0,
    @APIDescription("URL which is polled, when the call waits for a condition: the URL of a GET, or the status URL of a POST or PUT")
    @APILabel("Status URL")
    val statusUrl: String? = null,
    @APIDescription("Outcome of the conditions on the last response: SUCCESS, FAILURE or PENDING. Absent when there is no condition, or when they could not be evaluated.")
    @APILabel("Condition")
    val condition: HttpCallConditionOutcome? = null,
)
