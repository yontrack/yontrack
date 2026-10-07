package net.nemerosa.ontrack.kdsl.acceptance.tests.core

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.spec.events
import org.junit.jupiter.api.Test
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

}
