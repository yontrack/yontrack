package net.nemerosa.ontrack.extension.findings.events

import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.support.StartupService
import org.springframework.stereotype.Component

/**
 * Registration of the [findings events][FindingsEvents].
 */
@Component
class FindingsEventsRegistration(
    private val eventFactory: EventFactory,
) : StartupService {

    override fun getName(): String = "Registration of the findings events"

    override fun startupOrder(): Int = StartupService.JOB_REGISTRATION

    override fun start() {
        eventFactory.register(FindingsEvents.SECURITY_FINDING_NEW)
        eventFactory.register(FindingsEvents.SECURITY_FINDING_RESOLVED)
    }
}
