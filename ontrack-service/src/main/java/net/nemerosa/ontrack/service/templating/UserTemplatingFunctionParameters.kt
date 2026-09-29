package net.nemerosa.ontrack.service.templating

import net.nemerosa.ontrack.common.api.APIDescription

data class UserTemplatingFunctionParameters(
    @APIDescription("Field to display for the user: `display` or `email`. Defaults to the email.")
    val field: UserTemplatingFunctionField? = null,
)
