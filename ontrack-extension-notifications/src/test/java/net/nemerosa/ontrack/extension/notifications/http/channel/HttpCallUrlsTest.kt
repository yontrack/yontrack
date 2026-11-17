package net.nemerosa.ontrack.extension.notifications.http.channel

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpCallUrlsTest {

    @ParameterizedTest
    @CsvSource(
        "https://api.example.com/v1, /deployments, https://api.example.com/v1/deployments",
        "https://api.example.com/v1, deployments, https://api.example.com/v1/deployments",
        "https://api.example.com/v1/, /deployments, https://api.example.com/v1/deployments",
        "https://api.example.com/v1, /deployments/12/status, https://api.example.com/v1/deployments/12/status",
        "https://api.example.com/v1, /deployments?env=prod&force=true, https://api.example.com/v1/deployments?env=prod&force=true",
        "https://api.example.com/v1, ?env=prod, https://api.example.com/v1?env=prod",
        "https://api.example.com, /deployments, https://api.example.com/deployments",
        "https://api.example.com/, deployments, https://api.example.com/deployments",
        "http://localhost:8080/api, /items:batchGet, http://localhost:8080/api/items:batchGet",
        "https://api.example.com/v1, /v1.2/release-1.0, https://api.example.com/v1/v1.2/release-1.0",
        "https://api.example.com/v1, /a%20b, https://api.example.com/v1/a%20b",
    )
    fun `Path appended to the base URL`(base: String, path: String, expected: String) {
        assertEquals(expected, resolveHttpCallUrl(base, path).toString())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "  "])
    fun `No path is the base URL itself`(path: String) {
        assertEquals(
            "https://api.example.com/v1",
            resolveHttpCallUrl("https://api.example.com/v1", path).toString()
        )
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            // Absolute URLs
            "https://evil.example.com/steal",
            "http://api.example.com/v1/deployments",
            "HTTPS://evil.example.com",
            "javascript:alert(1)",
            "file:///etc/passwd",
            // Network-path references
            "//evil.example.com/steal",
            // Escapes from the base path
            "..",
            "../admin",
            "/../admin",
            "/deployments/../../admin",
            "/./deployments",
            "/deployments/..",
            // Encoded dot segments
            "/%2e%2e/admin",
            "/%2E%2E/admin",
            "/.%2e/admin",
            "/..%2fadmin",
            // Backslashes, read as slashes by some servers
            "\\..\\admin",
            "/a\\b",
            // Fragments are never sent
            "/deployments#top",
            // Not a URL at all
            "/deployments with spaces",
        ]
    )
    fun `Paths refused`(path: String) {
        val ex = assertFailsWith<HttpCallUrlException> {
            resolveHttpCallUrl("https://api.example.com/v1", path)
        }
        assertTrue(ex.message!!.contains("https://api.example.com/v1"), "The message names the base URL: ${ex.message}")
    }

    @Test
    fun `A sibling path sharing the prefix of the base path is out of it`() {
        // `/v1` must not accept `/v10`: the base path is a whole segment
        assertEquals(
            "https://api.example.com/v1/0",
            resolveHttpCallUrl("https://api.example.com/v1", "0").toString()
        )
    }

    @Test
    fun `The message of a refused path names the path`() {
        val ex = assertFailsWith<HttpCallUrlException> {
            resolveHttpCallUrl("https://api.example.com/v1", "https://evil.example.com")
        }
        assertEquals(
            """Path "https://evil.example.com" cannot be called under the base URL "https://api.example.com/v1": an absolute URL is not accepted, give a path under the base URL.""",
            ex.message
        )
    }
}
