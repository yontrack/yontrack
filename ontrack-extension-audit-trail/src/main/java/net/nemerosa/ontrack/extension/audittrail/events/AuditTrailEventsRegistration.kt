package net.nemerosa.ontrack.extension.audittrail.events

import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.support.StartupService
import org.springframework.stereotype.Component

/**
 * Registration of the [events of the audit trail][AuditTrailEvents].
 */
@Component
class AuditTrailEventsRegistration(
    private val eventFactory: EventFactory,
) : StartupService {

    override fun getName(): String = "Registration of the audit trail events"

    override fun startupOrder(): Int = StartupService.JOB_REGISTRATION

    override fun start() {
        eventFactory.register(AuditTrailEvents.TRAIL_VERIFICATION_FAILED)
    }
}
