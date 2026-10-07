package net.nemerosa.ontrack.service.events

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventFilter
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.events.EventType
import net.nemerosa.ontrack.model.security.EventsAudit
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Audit view of the events, through [EventQueryService.findEvents].
 *
 * The `EVENTS` table is shared by all the tests, so every test posts its events with a unique user
 * name and filters on it.
 */
@AsAdminTest
class EventsAuditIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    @Autowired
    private lateinit var eventPostService: EventPostService

    private val baseTime: LocalDateTime = LocalDateTime.of(2020, 6, 1, 12, 0, 0)

    /**
     * Project of the events which do not need a specific one.
     */
    private val defaultProject: Project by lazy { project() }

    /**
     * Posts an event about the [project], signed by the [user] at the [time], optionally
     * about an [extra] project.
     */
    private fun postEvent(
        user: String,
        time: LocalDateTime = baseTime,
        project: Project = defaultProject,
        extra: Project? = null,
        eventType: EventType = EventFactory.UPDATE_PROJECT,
    ) {
        val builder = Event.of(eventType).with(Signature.of(time, user)).withProject(project)
        extra?.let { builder.withExtra(it) }
        asAdmin {
            eventPostService.post(builder.build())
        }
    }

    private fun find(filter: EventFilter, offset: Int = 0, size: Int = 20): List<Event> =
        asAdmin {
            eventQueryService.findEvents(filter, offset, size).pageItems
        }

    @Test
    fun `Only the administrator role has the events audit function`() {
        Roles.GLOBAL_ROLES.forEach { id ->
            val role = rolesService.getGlobalRole(id).orElseThrow()
            if (id == Roles.GLOBAL_ADMINISTRATOR) {
                assertTrue(EventsAudit::class.java in role.globalFunctions, "Administrator has the events audit")
            } else {
                assertTrue(EventsAudit::class.java !in role.globalFunctions, "${role.id} has not the events audit")
            }
        }
    }

    @Test
    fun `Finding events is refused without the events audit function`() {
        asUser {
            assertFailsWith<AccessDeniedException> {
                eventQueryService.findEvents(EventFilter(), 0, 20)
            }
        }
    }

    @Test
    fun `Finding events on a case-insensitive user prefix`() {
        val prefix = uid("ev")
        postEvent(user = "${prefix}ALICE")
        postEvent(user = "${prefix}alicia")
        postEvent(user = "${prefix}bob")
        assertEquals(
            listOf("${prefix}alicia", "${prefix}ALICE"),
            find(EventFilter(user = "${prefix}Ali".uppercase())).map { it.signature?.user?.name },
        )
    }

    @Test
    fun `The user prefix escapes the LIKE wildcards`() {
        val prefix = uid("ev")
        postEvent(user = "${prefix}_a")
        postEvent(user = "${prefix}xa")
        postEvent(user = "${prefix}%b")
        postEvent(user = "${prefix}yyb")
        assertEquals(
            listOf("${prefix}_a"),
            find(EventFilter(user = "${prefix}_")).map { it.signature?.user?.name },
        )
        assertEquals(
            listOf("${prefix}%b"),
            find(EventFilter(user = "${prefix}%")).map { it.signature?.user?.name },
        )
    }

    @Test
    fun `The date bounds are inclusive`() {
        val user = uid("ev")
        postEvent(user = user, time = baseTime.minusSeconds(1))
        postEvent(user = user, time = baseTime)
        postEvent(user = user, time = baseTime.plusHours(1))
        postEvent(user = user, time = baseTime.plusHours(1).plusSeconds(1))
        assertEquals(
            listOf(baseTime.plusHours(1), baseTime),
            find(EventFilter(user = user, from = baseTime, to = baseTime.plusHours(1))).map { it.signature?.time },
        )
        assertEquals(
            listOf(baseTime.plusHours(1).plusSeconds(1), baseTime.plusHours(1)),
            find(EventFilter(user = user, from = baseTime.plusHours(1))).map { it.signature?.time },
        )
        assertEquals(
            listOf(baseTime, baseTime.minusSeconds(1)),
            find(EventFilter(user = user, to = baseTime)).map { it.signature?.time },
        )
    }

    @Test
    fun `Finding events by event types`() {
        val user = uid("ev")
        postEvent(user = user, eventType = EventFactory.UPDATE_PROJECT)
        postEvent(user = user, eventType = EventFactory.DISABLE_PROJECT)
        postEvent(user = user, eventType = EventFactory.ENABLE_PROJECT)
        assertEquals(
            listOf(EventFactory.ENABLE_PROJECT.id, EventFactory.UPDATE_PROJECT.id),
            find(
                EventFilter(
                    user = user,
                    eventTypes = listOf(EventFactory.UPDATE_PROJECT.id, EventFactory.ENABLE_PROJECT.id),
                )
            ).map { it.eventType.id },
        )
        assertEquals(
            3,
            find(EventFilter(user = user, eventTypes = emptyList())).size,
            "No event type means all of them",
        )
    }

    @Test
    fun `Finding events by project matches the extra project too`() {
        val user = uid("ev")
        val project = project()
        val other = project()
        postEvent(user = user, project = project)
        postEvent(user = user, project = other, extra = project)
        postEvent(user = user, project = other)
        postEvent(user = user)
        val events = find(EventFilter(user = user, project = project.name))
        assertEquals(2, events.size)
        events[0].let { event ->
            assertEquals(other.id, event.entities[ProjectEntityType.PROJECT]?.id)
            assertEquals(project.id, event.extraEntities[ProjectEntityType.PROJECT]?.id)
        }
        events[1].let { event ->
            assertEquals(project.id, event.entities[ProjectEntityType.PROJECT]?.id)
            assertNull(event.extraEntities[ProjectEntityType.PROJECT])
        }
    }

    @Test
    fun `Finding events by an unknown project returns no event`() {
        val user = uid("ev")
        postEvent(user = user, project = project())
        postEvent(user = user)
        assertEquals(
            emptyList(),
            find(EventFilter(user = user, project = uid("unknown"))),
        )
    }

    @Test
    fun `Combining the filters`() {
        val user = uid("ev")
        val project = project()
        postEvent(user = user, project = project, time = baseTime, eventType = EventFactory.UPDATE_PROJECT)
        postEvent(user = user, project = project, time = baseTime, eventType = EventFactory.DISABLE_PROJECT)
        postEvent(user = user, project = project, time = baseTime.plusDays(1), eventType = EventFactory.UPDATE_PROJECT)
        postEvent(user = user, project = project(), time = baseTime, eventType = EventFactory.UPDATE_PROJECT)
        postEvent(user = "${user}-other", project = project, time = baseTime, eventType = EventFactory.UPDATE_PROJECT)
        val events = find(
            EventFilter(
                from = baseTime,
                to = baseTime,
                user = user,
                eventTypes = listOf(EventFactory.UPDATE_PROJECT.id),
                project = project.name,
            )
        )
        assertEquals(2, events.size)
        assertEquals(setOf(user, "${user}-other"), events.map { it.signature?.user?.name }.toSet())
    }

    @Test
    fun `Events are returned newest first and paginated`() {
        val user = uid("ev")
        // Posted in reverse chronological order: the order is the one of the IDs, not of the times
        (1..5).forEach { no ->
            postEvent(user = user, time = baseTime.minusMinutes(no.toLong()))
        }
        val all = asAdmin { eventQueryService.findEvents(EventFilter(user = user), 0, 20) }
        assertEquals(
            (5 downTo 1).map { baseTime.minusMinutes(it.toLong()) },
            all.pageItems.map { it.signature?.time },
        )
        assertNull(all.pageInfo.nextPage, "No next page")

        val first = asAdmin { eventQueryService.findEvents(EventFilter(user = user), 0, 2) }
        assertEquals(all.pageItems.take(2).map { it.id }, first.pageItems.map { it.id })
        assertNotNull(first.pageInfo.nextPage, "Next page") {
            assertEquals(2, it.offset)
            assertEquals(2, it.size)
        }

        val last = asAdmin { eventQueryService.findEvents(EventFilter(user = user), 4, 2) }
        assertEquals(all.pageItems.drop(4).map { it.id }, last.pageItems.map { it.id })
        assertNull(last.pageInfo.nextPage, "No next page after the last one")
        assertNotNull(last.pageInfo.previousPage, "Previous page") {
            assertEquals(2, it.offset)
            assertEquals(2, it.size)
        }
    }

    @Test
    fun `The page size is capped`() {
        val user = uid("ev")
        repeat(EventQueryService.MAX_EVENTS_PAGE_SIZE + 1) {
            postEvent(user = user)
        }
        val page = asAdmin { eventQueryService.findEvents(EventFilter(user = user), 0, 500) }
        assertEquals(EventQueryService.MAX_EVENTS_PAGE_SIZE, page.pageItems.size)
        assertNotNull(page.pageInfo.nextPage, "Next page")
    }

    @Test
    fun `An events auditor sees the events of the projects they cannot see`() {
        val user = uid("ev")
        withNoGrantViewToAll {
            val project = project()
            val other = project()
            postEvent(user = user, project = project)
            postEvent(user = user, project = other, extra = project)
            val events = asUserWith<EventsAudit, List<Event>> {
                eventQueryService.findEvents(EventFilter(user = user), 0, 20).pageItems
            }
            assertEquals(2, events.size)
            events[0].let { event ->
                assertEquals(other.name, (event.entities[ProjectEntityType.PROJECT] as Project).name)
                assertEquals(project.name, (event.extraEntities[ProjectEntityType.PROJECT] as Project).name)
            }
            events[1].let { event ->
                assertEquals(project.name, (event.entities[ProjectEntityType.PROJECT] as Project).name)
            }
        }
    }

}
