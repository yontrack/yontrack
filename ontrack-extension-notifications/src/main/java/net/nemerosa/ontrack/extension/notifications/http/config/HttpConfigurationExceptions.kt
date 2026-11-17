package net.nemerosa.ontrack.extension.notifications.http.config

import net.nemerosa.ontrack.model.exceptions.InputException

class HttpConfigurationUrlException(url: String, reason: String) : InputException(
    """Base URL "$url" is not valid: $reason."""
)
