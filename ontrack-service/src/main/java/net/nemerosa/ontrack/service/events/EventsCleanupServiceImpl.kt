package net.nemerosa.ontrack.service.events

import net.nemerosa.ontrack.model.events.EventsCleanupService
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.settings.EventsSettings
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.repository.EventRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * Not transactional on purpose: each batch is committed on its own, so that the locks on the
 * `EVENTS` table stay short.
 */
@Service
class EventsCleanupServiceImpl(
    private val cachedSettingsService: CachedSettingsService,
    private val eventRepository: EventRepository,
    private val ontrackConfigProperties: OntrackConfigProperties,
) : EventsCleanupService {

    private val logger: Logger = LoggerFactory.getLogger(EventsCleanupServiceImpl::class.java)

    override fun cleanup(now: LocalDateTime): Int {
        val retentionDays = cachedSettingsService.getCachedSettings(EventsSettings::class.java).retentionDays
        if (retentionDays <= 0) {
            logger.debug("[events] Cleanup: no retention, the events are kept")
            return 0
        }
        val before = now.minusDays(retentionDays.toLong())
        val batchSize = ontrackConfigProperties.events.cleanup.batchSize
        var total = 0
        do {
            val deleted = eventRepository.deleteEventsBefore(before, batchSize)
            total += deleted
        } while (deleted >= batchSize)
        logger.info("[events] Cleanup: deleted $total events posted before $before (retention of $retentionDays days)")
        return total
    }
}
