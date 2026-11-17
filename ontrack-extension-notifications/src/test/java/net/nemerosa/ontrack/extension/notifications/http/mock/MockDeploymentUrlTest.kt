package net.nemerosa.ontrack.extension.notifications.http.mock

import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertEquals

class MockDeploymentUrlTest {

    @Test
    fun `Port the backend is actually listening on`() {
        val environment = MockEnvironment()
            .withProperty("local.server.port", "54321")
            .withProperty("server.port", "0")
        assertEquals(
            "http://localhost:54321/extension/notifications/http/mock",
            mockDeploymentsBaseUrl(environment),
        )
    }

    @Test
    fun `Configured port when the server has not reported one`() {
        val environment = MockEnvironment()
            .withProperty("server.port", "9090")
        assertEquals(
            "http://localhost:9090/extension/notifications/http/mock",
            mockDeploymentsBaseUrl(environment),
        )
    }

    @Test
    fun `Default port`() {
        assertEquals(
            "http://localhost:8080/extension/notifications/http/mock",
            mockDeploymentsBaseUrl(MockEnvironment()),
        )
    }

    @Test
    fun `Context path`() {
        val environment = MockEnvironment()
            .withProperty("local.server.port", "8080")
            .withProperty("server.servlet.context-path", "/ontrack/")
        assertEquals(
            "http://localhost:8080/ontrack/extension/notifications/http/mock",
            mockDeploymentsBaseUrl(environment),
        )
    }

    @Test
    fun `HTTPS when the backend serves it`() {
        val environment = MockEnvironment()
            .withProperty("local.server.port", "8443")
            .withProperty("server.ssl.enabled", "true")
        assertEquals(
            "https://localhost:8443/extension/notifications/http/mock",
            mockDeploymentsBaseUrl(environment),
        )
    }
}
