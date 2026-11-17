package net.nemerosa.ontrack.extension.notifications.http.channel

import net.nemerosa.ontrack.model.exceptions.InputException

/**
 * A configuration of the `http` channel which cannot be used.
 */
open class HttpCallConfigException(message: String) : InputException(message)

/**
 * A path which cannot be called under the base URL of an HTTP configuration.
 */
class HttpCallUrlException(baseUrl: String, path: String, val reason: String) : HttpCallConfigException(
    """Path "$path" cannot be called under the base URL "$baseUrl": $reason."""
)
