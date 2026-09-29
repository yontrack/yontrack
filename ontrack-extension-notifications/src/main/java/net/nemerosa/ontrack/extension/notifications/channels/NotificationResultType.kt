package net.nemerosa.ontrack.extension.notifications.channels

enum class NotificationResultType(
    val running: Boolean,
) {

    OK(running = false),

    ONGOING(running = true),

    NOT_CONFIGURED(running = false),

    INVALID_CONFIGURATION(running = false),

    DISABLED(running = false),

    ERROR(running = false),

    /**
     * Introduced in 4.10.3 and no longer set, but kept: notification results stored with it are
     * still read.
     */
    TIMEOUT(running = false),

    ASYNC(running = true),

}