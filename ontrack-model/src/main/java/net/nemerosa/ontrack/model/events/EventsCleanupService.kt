package net.nemerosa.ontrack.model.events

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.model.settings.EventsSettings
import java.time.LocalDateTime

/**
 * Deletion of the events older than the [retention][EventsSettings.retentionDays] of the events.
 */
interface EventsCleanupService {

    /**
     * Deletes the events posted more than [EventsSettings.retentionDays] days before [now], in
     * batches of `ontrack.config.events.cleanup.batch-size` events. Does nothing when the retention
     * is `0`.
     *
     * @param now Time the retention is counted back from, in UTC
     * @return Number of deleted events
     */
    fun cleanup(now: LocalDateTime = Time.now()): Int

}
