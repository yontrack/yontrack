package net.nemerosa.ontrack.boot.ui

import com.opencsv.CSVReader
import net.nemerosa.ontrack.boot.Application
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.TokenOptions
import net.nemerosa.ontrack.model.structure.TokensConstants
import net.nemerosa.ontrack.model.structure.TokensService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Export of the events as CSV or JSON, on `GET /rest/admin/events/export`, over HTTP through the
 * whole stack — security, parameters, headers, streaming (#2016).
 *
 * The header row of the CSV and the field names of the JSON are those of the version 1 of the
 * format: a change to them fails these tests until the version is decided on.
 *
 * Not transactional: the server answers on its own threads, and must find what the test posted.
 */
@SpringBootTest(
    classes = [Application::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EventsExportHttpIT : AbstractDSLTestSupport() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var tokensService: TokensService

    @Autowired
    private lateinit var eventPostService: EventPostService

    private val client: HttpClient = HttpClient.newHttpClient()

    /**
     * Version 1 of the CSV columns.
     */
    private val csvHeaderV1 = listOf(
        "id", "time", "user", "eventType", "message",
        "project", "branch", "build", "promotionLevel", "validationStamp", "promotionRun", "validationRun",
        "xProject", "xBranch", "xBuild", "xPromotionLevel", "xValidationStamp", "xPromotionRun", "xValidationRun",
        "ref", "values",
    )

    /**
     * Version 1 of the fields of an event in the JSON.
     */
    private val jsonEventFieldsV1 = csvHeaderV1

    /**
     * Events posted by a user of their own, so that a filter on this user isolates them:
     *
     * * 12:00 - `update_project` on [project], renaming it
     * * 13:00 - `update_project` referring to [project], with the [otherBranch] as extra entities
     * * 14:00 - `disable_project` on [project]
     * * 15:00 - `update_project` on [other]
     */
    private inner class Events {
        val user = uid("ev")
        val project: Project
        val other: Project
        val otherBranch: Branch

        init {
            val time = LocalDateTime.of(2020, 6, 1, 12, 0, 0)
            project = asAdmin { project() }
            other = asAdmin { project() }
            otherBranch = asAdmin { other.branch() }
            asAdmin {
                eventPostService.post(
                    Event.of(EventFactory.UPDATE_PROJECT)
                        .withProject(project)
                        .with(EventFactory.PREVIOUS_NAME, "previous-name")
                        .with(Signature.of(time, user))
                        .build()
                )
                eventPostService.post(
                    Event.of(EventFactory.UPDATE_PROJECT)
                        .withRef(project)
                        .withExtra(otherBranch)
                        .with(Signature.of(time.plusHours(1), user))
                        .build()
                )
                eventPostService.post(
                    Event.of(EventFactory.DISABLE_PROJECT)
                        .withProject(project)
                        .with(Signature.of(time.plusHours(2), user))
                        .build()
                )
                eventPostService.post(
                    Event.of(EventFactory.UPDATE_PROJECT)
                        .withProject(other)
                        .with(Signature.of(time.plusHours(3), user))
                        .build()
                )
            }
        }
    }

    /**
     * Token of an administrator, who holds the events audit.
     */
    private fun auditorToken(): String =
        asAdmin { asGlobalRole(Roles.GLOBAL_ADMINISTRATOR) }.call {
            tokensService.generateNewToken(TokenOptions(name = uid("auditor-"))).value
        }

    /**
     * Token of a user without any role.
     */
    private fun userToken(): String =
        asUser().call {
            tokensService.generateNewToken(TokenOptions(name = uid("user-"))).value
        }

    private fun export(token: String, vararg parameters: Pair<String, String>): HttpResponse<String> {
        val query = parameters.joinToString("&") { (name, value) ->
            "$name=${URLEncoder.encode(value, Charsets.UTF_8)}"
        }
        return client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/rest/admin/events/export?$query"))
                .header(TokensConstants.HTTP_ONTRACK_TOKEN, token)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(Charsets.UTF_8),
        )
    }

    private fun HttpResponse<*>.header(name: String): String? = headers().firstValue(name).orElse(null)

    private fun HttpResponse<String>.csv(): List<List<String>> =
        CSVReader(StringReader(body())).use { reader -> reader.readAll().map { it.toList() } }

    private fun JsonNode.fieldNames(): List<String> = properties().map { it.key }

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
    fun `CSV export of the filtered events, newest first`() {
        val events = Events()
        val response = export(
            auditorToken(),
            "format" to "csv",
            "user" to events.user.uppercase(),
            "project" to events.project.name,
            "eventTypes" to EventFactory.UPDATE_PROJECT.id,
            "eventTypes" to EventFactory.DISABLE_PROJECT.id,
        )
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("text/csv;charset=UTF-8", response.header("Content-Type")?.replace(" ", ""))
        val disposition = response.header("Content-Disposition") ?: ""
        assertTrue(
            Regex("""attachment; filename="yontrack-events-\d{8}-\d{6}\.csv"""").matches(disposition),
            "Attachment: $disposition"
        )
        assertEquals("1", response.header("X-Yontrack-Export-Format-Version"))
        assertEquals("false", response.header("X-Yontrack-Export-Truncated"))

        val rows = response.csv()
        assertEquals(csvHeaderV1, rows.first(), "Header row of the version 1")
        val items = rows.drop(1).map { row -> csvHeaderV1.zip(row).toMap() }
        assertEquals(
            listOf(
                "2020-06-01T14:00:00Z" to "disable_project",
                "2020-06-01T13:00:00Z" to "update_project",
                "2020-06-01T12:00:00Z" to "update_project",
            ),
            items.map { it["time"] to it["eventType"] },
            "Events of the project, filtered, newest first"
        )
        val ids = items.map { it.getValue("id").toInt() }
        assertEquals(ids.sortedDescending(), ids)

        val (disabled, referring, renamed) = items
        assertEquals(events.user, disabled["user"])
        assertEquals("Project ${events.project.name} has been disabled.", disabled["message"])
        assertEquals(events.project.name, disabled["project"])
        assertEquals("", disabled["branch"])
        assertEquals("", disabled["xProject"])
        assertEquals("", disabled["ref"])
        assertEquals("{}", disabled["values"])

        assertEquals(events.project.name, referring["project"])
        assertEquals(events.other.name, referring["xProject"])
        assertEquals(events.otherBranch.name, referring["xBranch"])
        assertEquals("PROJECT", referring["ref"])

        assertEquals(
            mapOf(EventFactory.PREVIOUS_NAME to "previous-name"),
            renamed.getValue("values").parseAsJson().properties().associate { it.key to it.value.asString() },
            "Values as JSON"
        )
    }

    @Test
    fun `JSON export of the filtered events, newest first`() {
        val events = Events()
        val response = export(
            auditorToken(),
            "format" to "json",
            "user" to events.user,
            "from" to "2020-06-01T12:30:00.000Z",
            "to" to "2020-06-01T15:00:00",
        )
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("application/json", response.header("Content-Type"))
        val disposition = response.header("Content-Disposition") ?: ""
        assertTrue(
            Regex("""attachment; filename="yontrack-events-\d{8}-\d{6}\.json"""").matches(disposition),
            "Attachment: $disposition"
        )
        assertEquals("1", response.header("X-Yontrack-Export-Format-Version"))
        assertEquals("false", response.header("X-Yontrack-Export-Truncated"))

        val json = response.body().parseAsJson()
        assertEquals(
            listOf("formatVersion", "exportedAt", "filter", "maxRows", "truncated", "events"),
            json.fieldNames(),
            "Fields of the version 1, `events` last"
        )
        assertEquals(1, json.path("formatVersion").asInt())
        assertTrue(json.path("exportedAt").asString().endsWith("Z"), "Exported at, in UTC")
        assertEquals(
            listOf("from", "to", "user", "eventTypes", "project"),
            json.path("filter").fieldNames(),
        )
        assertEquals("2020-06-01T12:30:00Z", json.path("filter").path("from").asString())
        assertEquals("2020-06-01T15:00:00Z", json.path("filter").path("to").asString())
        assertEquals(events.user, json.path("filter").path("user").asString())
        assertTrue(json.path("filter").path("project").isNull)
        assertEquals(100_000, json.path("maxRows").asInt())
        assertEquals(false, json.path("truncated").asBoolean())

        val items = json.path("events").values().toList()
        assertEquals(
            listOf("2020-06-01T15:00:00Z", "2020-06-01T14:00:00Z", "2020-06-01T13:00:00Z"),
            items.map { it.path("time").asString() },
            "Events in the time range, newest first"
        )
        items.forEach { item ->
            assertEquals(jsonEventFieldsV1, item.fieldNames(), "Fields of an event in the version 1")
        }
        val (other, disabled, referring) = items
        assertEquals(events.other.name, other.path("project").asString())
        assertEquals("Project ${events.other.name} has been updated.", other.path("message").asString())
        assertTrue(disabled.path("branch").isNull)
        assertTrue(disabled.path("values").isObject)
        assertEquals(0, disabled.path("values").size())
        assertEquals(events.otherBranch.name, referring.path("xBranch").asString())
        assertEquals("PROJECT", referring.path("ref").asString())
        assertEquals(events.user, referring.path("user").asString())
        assertEquals("update_project", referring.path("eventType").asString())
        assertTrue(referring.path("id").isInt)
    }

    @Test
    fun `CSV export truncated beyond the maximum number of rows`() {
        val events = Events()
        val response = withMaxRows(3) {
            export(auditorToken(), "format" to "csv", "user" to events.user)
        }
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("true", response.header("X-Yontrack-Export-Truncated"))
        val rows = response.csv()
        assertEquals(csvHeaderV1, rows.first())
        val items = rows.drop(1).map { row -> csvHeaderV1.zip(row).toMap() }
        assertEquals(
            listOf("2020-06-01T15:00:00Z", "2020-06-01T14:00:00Z", "2020-06-01T13:00:00Z", ""),
            items.map { it["time"] },
            "The 3 most recent events, then the marker"
        )
        val marker = items.last()
        assertEquals("TRUNCATED", marker["id"])
        assertEquals("Export limited to 3 events: narrow the filter", marker["message"])
    }

    @Test
    fun `JSON export truncated beyond the maximum number of rows`() {
        val events = Events()
        val response = withMaxRows(3) {
            export(auditorToken(), "format" to "json", "user" to events.user)
        }
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("true", response.header("X-Yontrack-Export-Truncated"))
        val json = response.body().parseAsJson()
        assertEquals(3, json.path("maxRows").asInt())
        assertEquals(true, json.path("truncated").asBoolean())
        assertEquals(
            listOf("2020-06-01T15:00:00Z", "2020-06-01T14:00:00Z", "2020-06-01T13:00:00Z"),
            json.path("events").values().map { it.path("time").asString() },
        )
    }

    @Test
    fun `Export not truncated when the events are exactly the maximum number of rows`() {
        val events = Events()
        val response = withMaxRows(4) {
            export(auditorToken(), "format" to "csv", "user" to events.user)
        }
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("false", response.header("X-Yontrack-Export-Truncated"))
        assertEquals(1 + 4, response.csv().size, "Header and the 4 events, no marker")
    }

    @Test
    fun `Export refused without the events audit`() {
        val response = export(userToken(), "format" to "csv")
        assertEquals(403, response.statusCode(), response.body())
    }

    @Test
    fun `Export refused for an unknown format`() {
        val response = export(auditorToken(), "format" to "xml")
        assertEquals(400, response.statusCode(), response.body())
    }

    @Test
    fun `Export refused for a time which cannot be parsed`() {
        val response = export(auditorToken(), "format" to "csv", "from" to "yesterday")
        assertEquals(400, response.statusCode(), response.body())
    }
}
