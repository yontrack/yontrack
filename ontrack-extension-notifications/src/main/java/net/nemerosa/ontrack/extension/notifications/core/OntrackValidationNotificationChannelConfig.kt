package net.nemerosa.ontrack.extension.notifications.core

import net.nemerosa.ontrack.common.api.APIDescription

data class OntrackValidationNotificationChannelConfig(
    @APIDescription("[template] Name of the project to validate. If not provided, looks for the event's project if available.")
    val project: String? = null,
    @APIDescription("[template] Name of the branch to validate. If not provided, looks for the event's branch if available.")
    val branch: String? = null,
    @APIDescription("[template] Name of the build to validate. If not provided, looks for the event's build if available.")
    val build: String? = null,
    @APIDescription("Name of the validation stamp to use.")
    val validation: String,
    @APIDescription("Run time. Can be a template must be rendered as a number of seconds.")
    val runTime: String? = null,
    @APIDescription("[template] Status of the validation run, must render to a valid validation run status ID (e.g. PASSED, FAILED). Defaults to PASSED.")
    val status: String? = null,
)