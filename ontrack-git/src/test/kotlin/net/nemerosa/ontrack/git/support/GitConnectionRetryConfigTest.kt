package net.nemerosa.ontrack.git.support

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpServerErrorException
import java.io.InterruptedIOException
import java.net.SocketException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitConnectionRetryConfigTest {

    @Test
    fun `Match by connection error - SocketException`() {
        val config = GitConnectionRetryConfig(connectionError = true)
        assertTrue(config.match(SocketException("Connection reset")))
    }

    @Test
    fun `Match by connection error - InterruptedIOException`() {
        val config = GitConnectionRetryConfig(connectionError = true)
        assertTrue(config.match(InterruptedIOException("Timeout")))
    }

    @Test
    fun `Does not match by connection error if not configured`() {
        val config = GitConnectionRetryConfig(connectionError = false)
        assertFalse(config.match(SocketException("Connection reset")))
    }

    @Test
    fun `Match by HTTP code - exact`() {
        val config = GitConnectionRetryConfig(httpCode = "503")
        assertTrue(config.match(HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable")))
        assertFalse(config.match(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error")))
    }

    @Test
    fun `Match by HTTP code - regex`() {
        val config = GitConnectionRetryConfig(httpCode = "50[0-3]")
        assertTrue(config.match(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error")))
        assertTrue(config.match(HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable")))
        assertFalse(config.match(HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT, "Timeout")))
    }

    @Test
    fun `Match by error message`() {
        val config = GitConnectionRetryConfig(httpCode = "500", errorMessage = ".*timeout.*")
        val exception = HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Operation timeout occurred")
        assertTrue(config.match(exception))
        assertFalse(config.match(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error")))
    }

    @Test
    fun `Match by HTTP code and error message`() {
        val config = GitConnectionRetryConfig(httpCode = "500", errorMessage = ".*retry.*")
        assertTrue(config.match(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Please retry later")))
        assertFalse(config.match(HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Please retry later")))
        assertFalse(config.match(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error")))
    }

    @Test
    fun `Does not match other exception types`() {
        val config = GitConnectionRetryConfig(connectionError = true, httpCode = ".*", errorMessage = ".*")
        assertFalse(config.match(RuntimeException("Some error")))
    }

    @Test
    fun `ToString with connection error only`() {
        val config = GitConnectionRetryConfig(connectionError = true, httpCode = "", errorMessage = ".*")
        assertEquals("GitConnectionRetryConfig(match: connection errors, retry: default)", config.toString())
    }

    @Test
    fun `ToString with HTTP code and message`() {
        val config = GitConnectionRetryConfig(
            httpCode = "50[03]",
            errorMessage = ".*timeout.*",
            retryLimit = 5,
            retryInterval = Duration.ofSeconds(10)
        )
        assertEquals(
            "GitConnectionRetryConfig(match: HTTP code matching '50[03]' and message matching '.*timeout.*', retry: limit=5, interval=PT10S)",
            config.toString()
        )
    }

    @Test
    fun `ToString with all conditions`() {
        val config = GitConnectionRetryConfig(
            connectionError = true,
            httpCode = "503",
            errorMessage = "Unavailable",
            retryLimit = 3,
            retryInterval = Duration.ofMillis(500)
        )
        assertEquals(
            "GitConnectionRetryConfig(match: connection errors and HTTP code matching '503' and message matching 'Unavailable', retry: limit=3, interval=PT0.5S)",
            config.toString()
        )
    }

    @Test
    fun `ToString with any error`() {
        val config = GitConnectionRetryConfig(httpCode = "", errorMessage = ".*", connectionError = false)
        assertEquals("GitConnectionRetryConfig(match: any error, retry: default)", config.toString())
    }

    @Test
    fun `Parsing of retry interval using seconds`() {
        val input = mapOf(
            "httpCode" to "50[03]",
            "connectionError" to true,
            "errorMessage" to ".*timeout.*",
            "retryLimit" to 5,
            "retryInterval" to "10s",
        ).asJson()
        val parsed = input.parse<GitConnectionRetryConfig>()
        assertEquals(
            GitConnectionRetryConfig(
                connectionError = true,
                httpCode = "50[03]",
                errorMessage = ".*timeout.*",
                retryLimit = 5,
                retryInterval = Duration.ofSeconds(10)
            ),
            parsed
        )
    }

    @Test
    fun `Parsing of retry interval using milliseconds`() {
        val input = mapOf(
            "httpCode" to "50[03]",
            "connectionError" to true,
            "errorMessage" to ".*timeout.*",
            "retryLimit" to 5,
            "retryInterval" to "500ms",
        ).asJson()
        val parsed = input.parse<GitConnectionRetryConfig>()
        assertEquals(
            GitConnectionRetryConfig(
                connectionError = true,
                httpCode = "50[03]",
                errorMessage = ".*timeout.*",
                retryLimit = 5,
                retryInterval = Duration.ofMillis(500)
            ),
            parsed
        )
    }

    @Test
    fun `Parsing of retry interval using format`() {
        val input = mapOf(
            "httpCode" to "50[03]",
            "connectionError" to true,
            "errorMessage" to ".*timeout.*",
            "retryLimit" to 5,
            "retryInterval" to "10s",
        ).asJson()
        val parsed = input.parse<GitConnectionRetryConfig>()
        assertEquals(
            GitConnectionRetryConfig(
                connectionError = true,
                httpCode = "50[03]",
                errorMessage = ".*timeout.*",
                retryLimit = 5,
                retryInterval = Duration.ofSeconds(10)
            ),
            parsed
        )
    }

    @Test
    fun `Parsing of retry interval using ISO format`() {
        val input = mapOf(
            "httpCode" to "50[03]",
            "connectionError" to true,
            "errorMessage" to ".*timeout.*",
            "retryLimit" to 5,
            "retryInterval" to "PT10S",
        ).asJson()
        val parsed = input.parse<GitConnectionRetryConfig>()
        assertEquals(
            GitConnectionRetryConfig(
                connectionError = true,
                httpCode = "50[03]",
                errorMessage = ".*timeout.*",
                retryLimit = 5,
                retryInterval = Duration.ofSeconds(10)
            ),
            parsed
        )
    }

    @Test
    fun `Formatting the retry interval`() {
        val config = GitConnectionRetryConfig(
            connectionError = true,
            httpCode = "50[03]",
            errorMessage = ".*timeout.*",
            retryLimit = 5,
            retryInterval = Duration.ofSeconds(10)
        )
        val json = config.asJson()
        assertEquals("10s", json.path("retryInterval").stringValue())
    }
}
