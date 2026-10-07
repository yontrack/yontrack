package net.nemerosa.ontrack.graphql.schema.events

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.security.EventsAudit
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.execution.ErrorType
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GQLRootQueryEventsIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var eventPostService: EventPostService

    private val query = """
        query Events(${'$'}offset: Int, ${'$'}size: Int, ${'$'}filter: EventFilterInput) {
            events(offset: ${'$'}offset, size: ${'$'}size, filter: ${'$'}filter) {
                pageInfo {
                    nextPage {
                        offset
                        size
                    }
                }
                pageItems {
                    id
                    eventType {
                        id
                        description
                    }
                    time
                    user
                    message
                    project {
                        name
                    }
                    entities {
                        type
                        id
                        displayName
                    }
                    extraEntities {
                        type
                        id
                        displayName
                    }
                    ref
                    values {
                        name
                        value
                    }
                }
            }
        }
    """

    @Test
    fun `Every field of an event`() {
        asAdmin {
            val user = uid("ev")
            val time = LocalDateTime.of(2020, 6, 1, 12, 0, 0)
            val project = project()
            val other = project()
            val otherBranch = other.branch()
            eventPostService.post(
                Event.of(EventFactory.UPDATE_PROJECT)
                    .withRef(project)
                    .withExtra(otherBranch)
                    .with(EventFactory.PREVIOUS_NAME, "previous-name")
                    .with(Signature.of(time, user))
                    .build()
            )
            run(query, mapOf("filter" to mapOf("user" to user))) { data ->
                val events = data.path("events")
                assertTrue(events.path("pageInfo").path("nextPage").isNull, "No next page")
                val items = events.path("pageItems")
                assertEquals(1, items.size())
                val event = items[0]
                assertTrue(event.path("id").asInt() > 0)
                assertEquals(EventFactory.UPDATE_PROJECT.id, event.path("eventType").path("id").asString())
                assertEquals(
                    EventFactory.UPDATE_PROJECT.description,
                    event.path("eventType").path("description").asString()
                )
                assertEquals("2020-06-01T12:00:00", event.path("time").asString())
                assertEquals(user, event.path("user").asString())
                val message = event.path("message").asString()
                assertTrue(message.startsWith("Project "), "Rendered message: $message")
                assertTrue(message.contains(project.name), "Rendered message contains the project: $message")
                assertTrue(message.contains("<a href="), "Rendered message is HTML: $message")
                assertEquals(project.name, event.path("project").path("name").asString())
                assertEquals(
                    listOf(Triple("PROJECT", project.id(), "Project ${project.name}")),
                    event.path("entities").values().map {
                        Triple(it.path("type").asString(), it.path("id").asInt(), it.path("displayName").asString())
                    }
                )
                assertEquals(
                    setOf(
                        Triple("PROJECT", other.id(), "Project ${other.name}"),
                        Triple("BRANCH", otherBranch.id(), otherBranch.entityDisplayName),
                    ),
                    event.path("extraEntities").values().map {
                        Triple(it.path("type").asString(), it.path("id").asInt(), it.path("displayName").asString())
                    }.toSet()
                )
                assertEquals("PROJECT", event.path("ref").asString())
                assertEquals(
                    listOf(EventFactory.PREVIOUS_NAME to "previous-name"),
                    event.path("values").values().map { it.path("name").asString() to it.path("value").asString() }
                )
            }
        }
    }

    @Test
    fun `Filtering and paginating the events`() {
        asAdmin {
            val user = uid("ev")
            val time = LocalDateTime.of(2020, 6, 1, 12, 0, 0)
            val project = project()
            (0..2).forEach { no ->
                eventPostService.post(
                    Event.of(EventFactory.UPDATE_PROJECT)
                        .withProject(project)
                        .with(Signature.of(time.plusHours(no.toLong()), user))
                        .build()
                )
            }
            eventPostService.post(
                Event.of(EventFactory.DISABLE_PROJECT)
                    .withProject(project)
                    .with(Signature.of(time, user))
                    .build()
            )
            run(
                query, mapOf(
                    "size" to 1,
                    "filter" to mapOf(
                        "user" to user.uppercase(),
                        "from" to "2020-06-01T12:00:00",
                        "to" to "2020-06-01T13:00:00",
                        "eventTypes" to listOf(EventFactory.UPDATE_PROJECT.id),
                        "project" to project.name,
                    )
                )
            ) { data ->
                val events = data.path("events")
                assertEquals(
                    listOf("2020-06-01T13:00:00"),
                    events.path("pageItems").values().map { it.path("time").asString() }
                )
                val nextPage = events.path("pageInfo").path("nextPage")
                assertEquals(1, nextPage.path("offset").asInt())
                assertEquals(1, nextPage.path("size").asInt())
            }
            run(
                query, mapOf(
                    "offset" to 1,
                    "size" to 1,
                    "filter" to mapOf(
                        "user" to user,
                        "from" to "2020-06-01T12:00:00",
                        "to" to "2020-06-01T13:00:00",
                        "eventTypes" to listOf(EventFactory.UPDATE_PROJECT.id),
                        "project" to project.name,
                    )
                )
            ) { data ->
                val events = data.path("events")
                assertEquals(
                    listOf("2020-06-01T12:00:00"),
                    events.path("pageItems").values().map { it.path("time").asString() }
                )
                assertTrue(events.path("pageInfo").path("nextPage").isNull, "No next page")
            }
        }
    }

    @Test
    fun `An events auditor can query the events`() {
        val user = uid("ev")
        asAdmin {
            eventPostService.post(
                Event.of(EventFactory.UPDATE_PROJECT)
                    .withProject(project())
                    .with(Signature.of(user))
                    .build()
            )
        }
        asUserWith<EventsAudit> {
            run(query, mapOf("filter" to mapOf("user" to user))) { data ->
                assertEquals(1, data.path("events").path("pageItems").size())
            }
        }
    }

    @Test
    fun `The events cannot be queried without the events audit function`() {
        asUser {
            runWithError(query, errorClassification = ErrorType.FORBIDDEN)
        }
    }

}
