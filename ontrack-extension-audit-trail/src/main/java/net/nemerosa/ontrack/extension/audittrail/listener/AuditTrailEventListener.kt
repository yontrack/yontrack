package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Writes the entries of the trails from the events of the changes they record.
 *
 * Events are posted synchronously, in the transaction of the change: the entries are committed or
 * rolled back with it. A change whose entry cannot be written fails — the trail records what was
 * committed, all of it.
 *
 * The **actor** of an entry is the one of the security context when the event is posted, never the
 * signature of the event, which is often the one of the build. Its **time** is the server's, at
 * append time ([TrailService.append]).
 *
 * This listener runs before the others: a change is recorded before what it sets off — an
 * auto-promotion posts its own event from the listener of the validation which triggered it.
 *
 * Nothing is mapped nor written while the licence is off.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class AuditTrailEventListener(
    mappers: List<TrailEventMapper>,
    private val auditTrailLicense: AuditTrailLicense,
    private val trailService: TrailService,
    private val securityService: SecurityService,
) : EventListener {

    private val mappersByEventType: Map<String, TrailEventMapper> =
        mappers.flatMap { mapper -> mapper.eventTypes.map { it to mapper } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (eventType, mappers) ->
                mappers.singleOrNull()
                    ?: error("Event type $eventType is mapped by several trail mappers: ${mappers.map { it::class.java.name }}")
            }

    override fun onEvent(event: Event) {
        val mapper = mappersByEventType[event.eventType.id] ?: return
        if (!auditTrailLicense.auditTrailEnabled) return
        // The actor is taken before reading as administrator, which would make it the system
        val actor = (securityService.currentActor ?: Actor.system(reason = null)).asJson()
        val entries = securityService.asAdmin { mapper.map(event) }
        entries.forEach { entry ->
            trailService.append(entry.build, entry.type, entry.payload, actor)
        }
    }
}
