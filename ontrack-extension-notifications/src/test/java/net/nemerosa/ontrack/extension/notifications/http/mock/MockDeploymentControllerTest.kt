package net.nemerosa.ontrack.extension.notifications.http.mock

import net.nemerosa.ontrack.json.parseAsJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MockDeploymentControllerTest {

    private val start = Instant.parse("2026-11-17T10:00:00Z")

    private fun controllerAt(now: Instant, contextPath: String = "") = MockDeploymentController(contextPath).apply {
        clock = Clock.fixed(now, ZoneOffset.UTC)
    }

    private fun create(body: String?, contextPath: String = "") =
        controllerAt(start, contextPath).create(body?.parseAsJson())

    @Test
    fun `The root of the API names where the deployments are`() {
        assertEquals(
            MockDeploymentRoot(deployments = "/yontrack/extension/notifications/http/mock/deployments"),
            controllerAt(start, contextPath = "/yontrack").root(),
        )
    }

    @Test
    fun `Creating a deployment returns it running, with its location`() {
        val response = create("""{"version": "1.0.0", "environment": "production"}""")
        assertEquals(HttpStatus.CREATED, response.statusCode)
        val body = assertNotNull(response.body as? MockDeploymentResponse)
        assertEquals(MockDeploymentStatus.RUNNING, body.status)
        assertEquals(
            "/extension/notifications/http/mock/deployments/${body.id}",
            response.headers.getFirst(HttpHeaders.LOCATION)
        )
    }

    @Test
    fun `A deployment is created running, even when it is over at once`() {
        val response = create("""{"outcome": "FAILED", "durationSeconds": 0}""")
        val body = assertNotNull(response.body as? MockDeploymentResponse)
        assertEquals(MockDeploymentStatus.RUNNING, body.status)
        assertEquals(MockDeploymentStatus.FAILED, controllerAt(start).get(body.id).body?.status)
    }

    @Test
    fun `The location carries the context path of the backend`() {
        val response = create(null, contextPath = "/yontrack")
        val body = assertNotNull(response.body as? MockDeploymentResponse)
        assertEquals(
            "/yontrack/extension/notifications/http/mock/deployments/${body.id}",
            response.headers.getFirst(HttpHeaders.LOCATION)
        )
    }

    @Test
    fun `A deployment runs for a few seconds by default, then succeeds`() {
        val id = assertNotNull(create(null).body as? MockDeploymentResponse).id
        assertEquals(MockDeploymentStatus.RUNNING, controllerAt(start.plusSeconds(4)).get(id).body?.status)
        assertEquals(MockDeploymentStatus.SUCCEEDED, controllerAt(start.plusSeconds(5)).get(id).body?.status)
    }

    @Test
    fun `A deployment can be asked to fail after a given duration`() {
        val id = assertNotNull(
            create("""{"outcome": "FAILED", "durationSeconds": 2}""").body as? MockDeploymentResponse
        ).id
        val running = controllerAt(start.plusSeconds(1)).get(id)
        assertEquals(HttpStatus.OK, running.statusCode)
        assertEquals(MockDeploymentStatus.RUNNING, running.body?.status)
        assertEquals(MockDeploymentStatus.FAILED, controllerAt(start.plusSeconds(2)).get(id).body?.status)
    }

    @Test
    fun `The outcome is read whatever its case`() {
        val id = assertNotNull(
            create("""{"outcome": "failed", "durationSeconds": 0}""").body as? MockDeploymentResponse
        ).id
        assertEquals(MockDeploymentStatus.FAILED, controllerAt(start).get(id).body?.status)
    }

    @Test
    fun `A succeeded outcome can be asked for explicitly`() {
        val id = assertNotNull(
            create("""{"outcome": "SUCCEEDED", "durationSeconds": 0}""").body as? MockDeploymentResponse
        ).id
        assertEquals(MockDeploymentStatus.SUCCEEDED, controllerAt(start).get(id).body?.status)
    }

    @Test
    fun `The deployment is returned with its id`() {
        val id = assertNotNull(create(null).body as? MockDeploymentResponse).id
        assertEquals(id, controllerAt(start).get(id).body?.id)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            """{"outcome": "CANCELLED"}""",
            """{"outcome": "RUNNING"}""",
            """{"outcome": 1}""",
            """{"durationSeconds": -1}""",
            """{"durationSeconds": 3601}""",
            """{"durationSeconds": 1.5}""",
            """{"durationSeconds": "5"}""",
            """["FAILED"]""",
        ]
    )
    fun `An invalid request is refused`(body: String) {
        val response = create(body)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val message = (response.body as? Map<*, *>)?.get("message") as? String
        assertTrue(!message.isNullOrBlank(), "The refusal says why")
    }

    @ParameterizedTest
    @ValueSource(strings = ["unknown", "1794909600000-5-succeeded", "1794909600000-5-succeeded-0a1b2c3d-x"])
    fun `An unknown deployment is not found`(id: String) {
        assertEquals(HttpStatus.NOT_FOUND, controllerAt(start).get(id).statusCode)
    }
}
