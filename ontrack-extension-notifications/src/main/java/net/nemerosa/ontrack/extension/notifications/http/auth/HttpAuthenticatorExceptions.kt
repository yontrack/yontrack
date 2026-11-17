package net.nemerosa.ontrack.extension.notifications.http.auth

import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.model.exceptions.InputException

class HttpAuthenticatorNotFoundException(type: String) : InputException(
    """Authentication type "$type" is not supported."""
)

class HttpAuthenticationConfigException(type: String, cause: JsonParseException) : InputException(
    cause,
    """Configuration for the "$type" authentication is invalid: ${cause.message}"""
)
