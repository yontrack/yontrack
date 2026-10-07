package net.nemerosa.ontrack.kdsl.acceptance.tests.core

import com.opencsv.CSVReader
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.spec.EventsExportFormat
import net.nemerosa.ontrack.kdsl.spec.events
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Audit of the events through `ontrack.events`.
 *
 * The events of a fresh project are the only ones filtered on its name, so the tests do not
 * depend on the other events of the instance.
 */
class ACCDSLEvents : AbstractACCDSLTestSupport() {

    @Test
    fun `Events of a project`() {
        val start = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)
        project {
            branch {
                build("1") {}
                build("2") {}
            }

            val events = ontrack.events(project = name)
            assertNull(events.nextOffset, "All the events in one page")
            assertEquals(
                listOf("new_build", "new_build", "new_branch", "new_project"),
                events.items.map { it.type },
                "Events of the project, newest first",
            )
            events.items.forEach { event ->
                assertEquals(name, event.project)
                assertTrue(event.time >= start, "Event time is recent")
                assertTrue(event.user.isNotBlank(), "Event has a user")
            }
            assertTrue(
                events.items.first().message.contains("2"),
                "The message of the last build is rendered: ${events.items.first().message}"
            )

            val builds = ontrack.events(project = name, eventTypes = listOf("new_build"))
            assertEquals(listOf("new_build", "new_build"), builds.items.map { it.type })

            val user = events.items.first().user
            val byUser = ontrack.events(project = name, user = user.uppercase(), from = start)
            assertEquals(events.items.map { it.id }, byUser.items.map { it.id })

            val none = ontrack.events(project = name, to = start)
            assertEquals(emptyList(), none.items, "No event before the project was created")

            val firstPage = ontrack.events(project = name, size = 3)
            assertEquals(events.items.take(3).map { it.id }, firstPage.items.map { it.id })
            assertNotNull(firstPage.nextOffset, "Next page") { nextOffset ->
                assertEquals(3, nextOffset)
                val secondPage = ontrack.events(project = name, offset = nextOffset, size = 3)
                assertEquals(events.items.drop(3).map { it.id }, secondPage.items.map { it.id })
                assertNull(secondPage.nextOffset, "No more pages")
            }
        }
    }

    @Test
    fun `Export of the events of a project`() {
        project {
            branch {
                build("1") {}
                build("2") {}
            }

            // CSV, filtered on the builds
            val csv = ontrack.events.export(
                format = EventsExportFormat.CSV,
                project = name,
                eventTypes = listOf("new_build"),
            )
            val rows = CSVReader(StringReader(csv)).use { it.readAll() }
            val header = rows.first().toList()
            val items = rows.drop(1).map { row -> header.zip(row).toMap() }
            assertEquals(
                listOf("new_build" to "2", "new_build" to "1"),
                items.map { it["eventType"] to it["build"] },
                "Builds of the project, newest first"
            )
            items.forEach { item ->
                assertEquals(name, item["project"])
                assertTrue(item["message"]?.contains("<") == false, "Plain text message: ${item["message"]}")
            }

            // JSON, all the events of the project
            val json = ontrack.events.export(
                format = EventsExportFormat.JSON,
                project = name,
            ).parseAsJson()
            assertEquals(1, json.path("formatVersion").asInt())
            assertEquals(false, json.path("truncated").asBoolean())
            assertEquals(name, json.path("filter").path("project").asString())
            assertEquals(
                listOf("new_build", "new_build", "new_branch", "new_project"),
                json.path("events").values().map { it.path("eventType").asString() },
                "Events of the project, newest first",
            )
        }
    }

}
