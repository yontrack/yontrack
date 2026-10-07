package net.nemerosa.ontrack.model.settings

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel

/**
 * Settings of the events.
 */
@APIDescription("Settings of the events")
data class EventsSettings(
    @APILabel("Retention (days)")
    @APIDescription("Number of days the events are kept. A daily job deletes the older ones. 0, the default, keeps the events forever. The deletion cannot be undone.")
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
) {
    companion object {
        /**
         * No retention: the events are kept forever.
         */
        const val DEFAULT_RETENTION_DAYS = 0
    }
}
