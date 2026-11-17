package net.nemerosa.ontrack.extension.notifications.http.channel

import net.nemerosa.ontrack.extension.notifications.http.auth.HttpAuthentication
import net.nemerosa.ontrack.extension.notifications.http.config.HttpConfiguration
import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpCallCelTest {

    private val configuration = HttpConfiguration(
        name = "deployments",
        url = "https://deploy.example.com/api",
        authentication = HttpAuthentication("header", mapOf("name" to "X-Api-Key", "value" to "secret").asJson()),
    )

    private fun response(
        status: Int = 200,
        body: String? = """{"id":"d-1","status":"RUNNING","count":3,"ratio":0.5,"done":false,"tags":["a","b"],"none":null}""",
        contentType: String? = "application/json",
        headers: HttpHeaders.() -> Unit = {},
    ) = HttpCallResponse(
        status = status,
        headers = HttpHeaders().apply {
            if (contentType != null) set(HttpHeaders.CONTENT_TYPE, contentType)
            headers()
        },
        body = body?.toByteArray() ?: ByteArray(0),
        truncated = false,
    )

    private fun context(response: HttpCallResponse = response(), attempt: Int = 1) =
        HttpCallCelContext.of(response, configuration, attempt)

    private fun condition(expression: String, response: HttpCallResponse = response(), attempt: Int = 1): Boolean =
        HttpCallCel.compileCondition("Success condition", expression).evaluate(context(response, attempt))

    // The environment

    @Test
    fun `Status as an int`() {
        assertTrue(condition("response.status == 200"))
        assertTrue(condition("response.status >= 200 && response.status < 300"))
        assertFalse(condition("response.status == 202"))
    }

    @Test
    fun `Body as parsed JSON`() {
        assertTrue(condition("response.body.status == 'RUNNING'"))
        assertTrue(condition("response.body.count == 3"))
        assertTrue(condition("response.body.ratio == 0.5"))
        assertTrue(condition("response.body.done == false"))
        assertTrue(condition("response.body.tags.size() == 2 && 'b' in response.body.tags"))
        assertTrue(condition("response.body.none == null"))
        assertTrue(condition("has(response.body.id) && !has(response.body.missing)"))
    }

    @Test
    fun `Body as a JSON array`() {
        assertTrue(
            condition(
                "response.body.exists(d, d.status == 'FAILED')",
                response(body = """[{"status":"OK"},{"status":"FAILED"}]"""),
            )
        )
    }

    @Test
    fun `Headers by lowercase name, with their first value`() {
        val response = response {
            add("Location", "/api/deployments/d-1")
            add("X-Multi", "first")
            add("X-Multi", "second")
        }
        assertTrue(condition("response.headers['location'] == '/api/deployments/d-1'", response))
        assertTrue(condition("response.headers['x-multi'] == 'first'", response))
        assertTrue(condition("response.headers['content-type'] == 'application/json'", response))
        assertFalse(condition("'Location' in response.headers", response))
    }

    @Test
    fun `No secret header`() {
        val response = response {
            add("Set-Cookie", "session=x")
            add("Authorization", "Bearer x")
            add("X-Api-Key", "secret")
        }
        assertTrue(
            condition(
                "!('set-cookie' in response.headers) && !('authorization' in response.headers) && !('x-api-key' in response.headers)",
                response
            )
        )
    }

    @Test
    fun `Attempt as an int`() {
        assertTrue(condition("attempt == 3", attempt = 3))
        assertTrue(condition("attempt > 2", attempt = 3))
    }

    @Test
    fun `Status URL from a header`() {
        val statusUrl = HttpCallCel.compileStatusUrl("response.headers['location']")
        assertEquals(
            "/api/deployments/d-1",
            statusUrl.evaluate(context(response { add("Location", "/api/deployments/d-1") }))
        )
    }

    @Test
    fun `Status URL from the body`() {
        val statusUrl = HttpCallCel.compileStatusUrl("'/deployments/' + response.body.id")
        assertEquals("/deployments/d-1", statusUrl.evaluate(context()))
    }

    // Compilation

    @Test
    fun `Syntax error`() {
        val ex = assertThrows<HttpCallCelCompilationException> {
            HttpCallCel.compileCondition("Success condition", "response.status ==")
        }
        assertTrue(ex.message!!.startsWith("Success condition `response.status ==` is not valid: "), ex.message)
    }

    @Test
    fun `Unknown variable`() {
        val ex = assertThrows<HttpCallCelCompilationException> {
            HttpCallCel.compileCondition("Failure condition", "request.status == 200")
        }
        assertTrue(ex.message!!.startsWith("Failure condition `request.status == 200` is not valid: "), ex.message)
        assertTrue(ex.message!!.contains("request"), ex.message)
    }

    @Test
    fun `Unknown field of the response`() {
        assertThrows<HttpCallCelCompilationException> {
            HttpCallCel.compileCondition("Success condition", "response.bdy.status == 'OK'")
        }
    }

    @Test
    fun `Status is an int, not a string`() {
        assertThrows<HttpCallCelCompilationException> {
            HttpCallCel.compileCondition("Success condition", "response.status == '200'")
        }
    }

    @Test
    fun `A condition must return a boolean`() {
        val ex = assertThrows<HttpCallCelCompilationException> {
            HttpCallCel.compileCondition("Success condition", "response.status")
        }
        assertTrue(ex.message!!.contains("bool"), ex.message)
    }

    @Test
    fun `A status URL must return a string`() {
        val ex = assertThrows<HttpCallCelCompilationException> {
            HttpCallCel.compileStatusUrl("response.status + 1")
        }
        assertTrue(ex.message!!.startsWith("Status URL `response.status + 1` is not valid: "), ex.message)
        assertTrue(ex.message!!.contains("string"), ex.message)
    }

    @Test
    fun `A blank expression is not valid`() {
        assertThrows<HttpCallCelCompilationException> {
            HttpCallCel.compileCondition("Success condition", " ")
        }
    }

    // Evaluation

    @Test
    fun `A non-JSON body cannot be evaluated`() {
        val ex = assertThrows<HttpCallCelEvaluationException> {
            context(response(body = "<html/>", contentType = "text/html"))
        }
        assertEquals("The response body is not JSON (text/html), conditions cannot be evaluated.", ex.message)
    }

    @Test
    fun `A JSON content type with an invalid body cannot be evaluated`() {
        assertThrows<HttpCallCelEvaluationException> {
            context(response(body = "{not json", contentType = "application/json"))
        }
    }

    @Test
    fun `An empty body cannot be evaluated`() {
        val ex = assertThrows<HttpCallCelEvaluationException> {
            context(response(body = null))
        }
        assertEquals("The response body is empty, conditions cannot be evaluated.", ex.message)
    }

    @Test
    fun `A truncated body cannot be evaluated`() {
        val ex = assertThrows<HttpCallCelEvaluationException> {
            HttpCallCelContext.of(
                HttpCallResponse(
                    status = 200,
                    headers = HttpHeaders().apply { set(HttpHeaders.CONTENT_TYPE, "application/json") },
                    body = """{"id":""".toByteArray(),
                    truncated = true,
                ),
                configuration,
                1,
            )
        }
        assertTrue(ex.message!!.contains("truncated"), ex.message)
    }

    @Test
    fun `A JSON body with a vendor content type`() {
        assertTrue(condition("response.body.status == 'RUNNING'", response(contentType = "application/vnd.api+json")))
    }

    @Test
    fun `A missing field of the body is an evaluation error naming the expression`() {
        val ex = assertThrows<HttpCallCelEvaluationException> {
            condition("response.body.missing == 'x'")
        }
        assertTrue(ex.message!!.startsWith("Success condition `response.body.missing == 'x'` could not be evaluated: "), ex.message)
    }

    @Test
    fun `A dynamic result which is not a boolean is an evaluation error`() {
        val ex = assertThrows<HttpCallCelEvaluationException> {
            condition("response.body.status")
        }
        assertTrue(ex.message!!.contains("did not return a boolean"), ex.message)
    }

    @Test
    fun `Evaluation is bounded`() {
        val list = (1..1000).joinToString(",", "[", "]")
        val ex = assertThrows<HttpCallCelEvaluationException> {
            condition("response.body.all(x, response.body.all(y, x + y > 0))", response(body = list))
        }
        assertTrue(ex.message!!.contains("cost limit"), ex.message)
    }
}
