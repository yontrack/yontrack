package net.nemerosa.ontrack.extension.notifications.http.channel

import net.nemerosa.ontrack.extension.notifications.http.config.HttpConfiguration
import net.nemerosa.ontrack.extension.notifications.http.settings.HttpNotificationSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpHeaders
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HttpCallConditionsTest {

    private val configuration = HttpConfiguration(name = "deployments", url = "https://deploy.example.com/api")

    private fun config(
        method: HttpCallMethod = HttpCallMethod.GET,
        successCondition: String? = null,
        failureCondition: String? = null,
        statusUrl: String? = null,
        intervalSeconds: Int = HttpNotificationChannelConfig.DEFAULT_INTERVAL_SECONDS,
        timeoutSeconds: Int = HttpNotificationChannelConfig.DEFAULT_TIMEOUT_SECONDS,
    ) = HttpNotificationChannelConfig(
        config = "deployments",
        method = method,
        successCondition = successCondition,
        failureCondition = failureCondition,
        statusUrl = statusUrl,
        intervalSeconds = intervalSeconds,
        timeoutSeconds = timeoutSeconds,
    )

    private fun context(status: String) = HttpCallCelContext.of(
        HttpCallResponse(
            status = 200,
            headers = HttpHeaders().apply { set(HttpHeaders.CONTENT_TYPE, "application/json") },
            body = """{"status":"$status"}""".toByteArray(),
            truncated = false,
        ),
        configuration,
        1,
    )

    // Compilation

    @Test
    fun `No condition`() {
        assertNull(HttpCallConditions.compile(config()))
    }

    @Test
    fun `Blank conditions are no conditions`() {
        assertNull(HttpCallConditions.compile(config(successCondition = " ", failureCondition = "", statusUrl = "")))
    }

    @Test
    fun `All conditions`() {
        val conditions = assertNotNull(
            HttpCallConditions.compile(
                config(
                    method = HttpCallMethod.POST,
                    successCondition = "response.body.status == 'SUCCEEDED'",
                    failureCondition = "response.body.status == 'FAILED'",
                    statusUrl = "response.headers['location']",
                )
            )
        )
        assertEquals("response.body.status == 'SUCCEEDED'", conditions.success.expression)
        assertEquals("response.body.status == 'FAILED'", conditions.failure?.expression)
        assertEquals("response.headers['location']", conditions.statusUrl?.expression)
    }

    @Test
    fun `A failure condition requires a success condition`() {
        val ex = assertThrows<HttpCallConfigException> {
            HttpCallConditions.compile(config(failureCondition = "response.status == 500"))
        }
        assertEquals("A failure condition requires a success condition.", ex.message)
    }

    @Test
    fun `A status URL requires a success condition`() {
        val ex = assertThrows<HttpCallConfigException> {
            HttpCallConditions.compile(config(method = HttpCallMethod.POST, statusUrl = "response.headers['location']"))
        }
        assertEquals("A status URL requires a success condition.", ex.message)
    }

    @Test
    fun `A status URL is not accepted on GET`() {
        val ex = assertThrows<HttpCallConfigException> {
            HttpCallConditions.compile(
                config(successCondition = "true", statusUrl = "response.headers['location']")
            )
        }
        assertEquals("A status URL is only used by POST and PUT: a GET polls its own URL.", ex.message)
    }

    @Test
    fun `An invalid failure condition is named`() {
        val ex = assertThrows<HttpCallCelCompilationException> {
            HttpCallConditions.compile(config(successCondition = "true", failureCondition = "response.status = 1"))
        }
        assertEquals(true, ex.message?.startsWith("Failure condition `response.status = 1` is not valid: "), ex.message)
    }

    @Test
    fun `An invalid status URL is named`() {
        val ex = assertThrows<HttpCallCelCompilationException> {
            HttpCallConditions.compile(
                config(method = HttpCallMethod.PUT, successCondition = "true", statusUrl = "response.status")
            )
        }
        assertEquals(true, ex.message?.startsWith("Status URL `response.status` is not valid: "), ex.message)
    }

    @Test
    fun `Interval of at least 5 seconds`() {
        val ex = assertThrows<HttpCallConfigException> {
            HttpCallConditions.compile(config(successCondition = "true", intervalSeconds = 4))
        }
        assertEquals("The interval must be at least 5 seconds.", ex.message)
    }

    @Test
    fun `Timeout of at least the interval`() {
        val ex = assertThrows<HttpCallConfigException> {
            HttpCallConditions.compile(config(successCondition = "true", intervalSeconds = 30, timeoutSeconds = 20))
        }
        assertEquals("The timeout (20 s) must be at least the interval (30 s).", ex.message)
    }

    // Outcomes

    @ParameterizedTest
    @CsvSource(
        "SUCCEEDED, SUCCESS",
        "FAILED, FAILURE",
        "RUNNING, PENDING",
    )
    fun `Tri-state outcome`(status: String, expected: HttpCallConditionOutcome) {
        val conditions = assertNotNull(
            HttpCallConditions.compile(
                config(
                    successCondition = "response.body.status == 'SUCCEEDED'",
                    failureCondition = "response.body.status == 'FAILED'",
                )
            )
        )
        assertEquals(expected, conditions.evaluate(context(status)))
    }

    @Test
    fun `Without a failure condition, anything else is pending`() {
        val conditions = assertNotNull(HttpCallConditions.compile(config(successCondition = "response.body.status == 'SUCCEEDED'")))
        assertEquals(HttpCallConditionOutcome.PENDING, conditions.evaluate(context("FAILED")))
    }

    @Test
    fun `The failure wins when both conditions hold`() {
        val conditions = assertNotNull(
            HttpCallConditions.compile(config(successCondition = "response.status == 200", failureCondition = "response.body.status == 'FAILED'"))
        )
        assertEquals(HttpCallConditionOutcome.FAILURE, conditions.evaluate(context("FAILED")))
    }

    // Timeout

    @ParameterizedTest
    @CsvSource(
        "300, 3600, 300",
        "3600, 3600, 3600",
        "7200, 3600, 3600",
        "300, 60, 60",
    )
    fun `Timeout capped by the settings`(timeout: Int, max: Int, expected: Int) {
        assertEquals(
            expected,
            config(timeoutSeconds = timeout).effectiveTimeoutSeconds(HttpNotificationSettings(maxTimeoutSeconds = max))
        )
    }
}
