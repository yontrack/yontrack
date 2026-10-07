package net.nemerosa.ontrack.boot.ui

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.it.deprecatedUsages
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The `/rest/events` endpoints are deprecated in V6, and removed in V7 (#2014): they still answer,
 * and each call is reported as a deprecated usage.
 */
@Suppress("DEPRECATION")
class EventControllerIT : AbstractWebTestSupport() {

    @Autowired
    private lateinit var controller: EventController

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @Test
    fun `The root events endpoint still answers and is reported as deprecated`() {
        val project = project()
        val usages = meterRegistry.deprecatedUsages(DeprecationSurface.REST, "GET /rest/events/root") {
            val events = controller.getEvents(0, 100)
            assertTrue(
                events.any { it.eventType == EventFactory.NEW_PROJECT.id && it.entities[ProjectEntityType.PROJECT]?.id == project.id },
                "The creation of the project is among the events"
            )
        }
        assertEquals(1.0, usages)
    }

    @Test
    fun `The entity events endpoint still answers and is reported as deprecated`() {
        val project = project()
        val usages = meterRegistry.deprecatedUsages(
            DeprecationSurface.REST,
            "GET /rest/events/{entityType}/{entityId}"
        ) {
            val events = controller.getEvents(ProjectEntityType.PROJECT, project.id, 0, 10)
            assertEquals(listOf(EventFactory.NEW_PROJECT.id), events.map { it.eventType })
        }
        assertEquals(1.0, usages)
    }
}
