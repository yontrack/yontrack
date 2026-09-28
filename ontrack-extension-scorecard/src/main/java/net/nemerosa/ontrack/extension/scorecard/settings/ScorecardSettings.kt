package net.nemerosa.ontrack.extension.scorecard.settings

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel

/**
 * Settings of the delivery scorecard.
 */
@APIDescription("Settings of the delivery scorecard")
data class ScorecardSettings(
    @APILabel("Window")
    @APIDescription("Number of days a reading is taken over, back from its computation. An estate may override it per reading.")
    val windowDays: Int = DEFAULT_WINDOW_DAYS,
    @APILabel("Retention")
    @APIDescription("Number of days the daily snapshots of the readings are kept")
    val retentionDays: Int = DEFAULT_RETENTION_DAYS,
    @APILabel("Schedule")
    @APIDescription("Cron schedule of the daily computation of the readings (seconds, minutes, hours, day of month, month, day of week), in the time zone of the server")
    val cron: String = DEFAULT_CRON,
) {
    companion object {
        const val DEFAULT_WINDOW_DAYS = 90
        const val DEFAULT_RETENTION_DAYS = 730
        const val DEFAULT_CRON = "0 0 2 * * *"
    }
}
