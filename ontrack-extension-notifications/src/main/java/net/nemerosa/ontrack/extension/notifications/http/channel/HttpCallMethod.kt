package net.nemerosa.ontrack.extension.notifications.http.channel

/**
 * Verbs of an HTTP call. There is no `DELETE`: the channel calls an API as a step of delivery, it does
 * not remove anything.
 */
enum class HttpCallMethod(
    /**
     * Can the request carry a body?
     */
    val acceptsBody: Boolean,
) {
    GET(acceptsBody = false),
    POST(acceptsBody = true),
    PUT(acceptsBody = true),
}
