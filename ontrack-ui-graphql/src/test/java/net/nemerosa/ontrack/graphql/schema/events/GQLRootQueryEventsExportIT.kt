package net.nemerosa.ontrack.graphql.schema.events

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.graphql.execution.ErrorType
import kotlin.test.assertEquals

class GQLRootQueryEventsExportIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var eventPostService: EventPostService

    private val query = """
        query EventsExport(${'$'}filter: EventFilterInput) {
            eventsExport(filter: ${'$'}filter) {
                maxRows
                truncated
            }
        }
    """

    /**
     * Posts [count] events by a user of their own, and returns the name of this user.
     */
    private fun events(count: Int): String {
        val user = uid("ev")
        val project = project()
        repeat(count) {
            eventPostService.post(
                Event.of(EventFactory.UPDATE_PROJECT)
                    .withProject(project)
                    .with(Signature.of(user))
                    .build()
            )
        }
        return user
    }

    private fun <T> withMaxRows(maxRows: Int, code: () -> T): T {
        val old = ontrackConfigProperties.events.export.maxRows
        ontrackConfigProperties.events.export.maxRows = maxRows
        return try {
            code()
        } finally {
            ontrackConfigProperties.events.export.maxRows = old
        }
    }

    @Test
    fun `Export of the events not truncated`() {
        asAdmin {
            val user = events(3)
            withMaxRows(3) {
                run(query, mapOf("filter" to mapOf("user" to user))) { data ->
                    val info = data.path("eventsExport")
                    assertEquals(3, info.path("maxRows").asInt())
                    assertEquals(false, info.path("truncated").asBoolean())
                }
            }
        }
    }

    @Test
    fun `Export of the events truncated`() {
        asAdmin {
            val user = events(4)
            withMaxRows(3) {
                run(query, mapOf("filter" to mapOf("user" to user))) { data ->
                    val info = data.path("eventsExport")
                    assertEquals(3, info.path("maxRows").asInt())
                    assertEquals(true, info.path("truncated").asBoolean())
                }
            }
        }
    }

    @Test
    fun `Export of the events with the default maximum`() {
        asAdmin {
            val user = events(1)
            run(query, mapOf("filter" to mapOf("user" to user))) { data ->
                val info = data.path("eventsExport")
                assertEquals(100_000, info.path("maxRows").asInt())
                assertEquals(false, info.path("truncated").asBoolean())
            }
        }
    }

    @Test
    fun `Export of the events refused without the events audit function`() {
        asUser {
            runWithError(query, errorClassification = ErrorType.FORBIDDEN)
        }
    }
}
