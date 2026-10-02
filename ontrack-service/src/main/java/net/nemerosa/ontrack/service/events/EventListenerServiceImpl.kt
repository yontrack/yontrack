package net.nemerosa.ontrack.service.events

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventListener
import net.nemerosa.ontrack.model.events.EventListenerService
import net.nemerosa.ontrack.model.metrics.time
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.AnnotationAwareOrderComparator
import org.springframework.stereotype.Service

@Service
class EventListenerServiceImpl(
    private val context: ApplicationContext,
    private val meterRegistry: MeterRegistry,
) : EventListenerService {

    // private val logger = LoggerFactory.getLogger(EventListenerServiceImpl::class.java)

    /**
     * Listeners, in their [order][org.springframework.core.annotation.Order], those without one in the
     * order of their registration.
     */
    private val listeners: List<EventListener> by lazy {
        context.getBeansOfType(
            EventListener::class.java
        ).values.sortedWith(AnnotationAwareOrderComparator.INSTANCE)
    }

    override fun onEvent(event: Event) {
        listeners.forEach {
            onEventForListener(it, event)
        }
    }

    private fun onEventForListener(eventListener: EventListener, event: Event) {
        meterRegistry.time(
            name = EventListenerMetrics.METRIC_ONTRACK_EVENT_LISTENER_TIME,
            EventListenerMetrics.TAG_EVENT_LISTENER to eventListener::class.java.name,
        ) {
            // logger.logTime("Event listener ${eventListener.javaClass.simpleName}") {
                eventListener.onEvent(event)
            // }
        }
    }
}
