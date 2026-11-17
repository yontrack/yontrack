package net.nemerosa.ontrack.extension.notifications.http.channel

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpCallStatusUrlsTest {

    @ParameterizedTest
    @CsvSource(
        // A path built by the expression is appended, as the path of the call
        "https://api.example.com/v1, /deployments/d-1, https://api.example.com/v1/deployments/d-1",
        "https://api.example.com/v1, deployments/d-1?full=true, https://api.example.com/v1/deployments/d-1?full=true",
        "https://api.example.com, /deployments/d-1, https://api.example.com/deployments/d-1",
        // A Location header holding an absolute path already under the base URL is taken as it is
        "https://api.example.com/v1, /v1/deployments/d-1, https://api.example.com/v1/deployments/d-1",
        "https://api.example.com/v1/, /v1/deployments/d-1?x=1, https://api.example.com/v1/deployments/d-1?x=1",
        "https://api.example.com/v1, /v1, https://api.example.com/v1",
        // A Location header holding an absolute URL under the base URL is taken as it is
        "https://api.example.com/v1, https://api.example.com/v1/deployments/d-1, https://api.example.com/v1/deployments/d-1",
        "https://api.example.com/v1, HTTPS://API.example.com/v1/deployments/d-1?x=1, https://api.example.com/v1/deployments/d-1?x=1",
        "https://api.example.com/v1, https://api.example.com:443/v1/deployments/d-1, https://api.example.com/v1/deployments/d-1",
        "http://localhost:8080/api, http://localhost:8080/api/deployments/d-1, http://localhost:8080/api/deployments/d-1",
        "https://api.example.com, https://api.example.com/deployments/d-1, https://api.example.com/deployments/d-1",
    )
    fun `Status URL under the base URL`(base: String, statusUrl: String, expected: String) {
        assertEquals(expected, resolveHttpCallStatusUrl(base, statusUrl).toString())
    }

    @ParameterizedTest
    @CsvSource(
        "https://api.example.com/v1, https://other.example.com/v1/deployments/d-1, outside the base URL",
        "https://api.example.com/v1, http://api.example.com/v1/deployments/d-1, outside the base URL",
        "https://api.example.com/v1, https://api.example.com:8443/v1/deployments/d-1, outside the base URL",
        "https://api.example.com/v1, https://api.example.com/v2/deployments/d-1, outside the base URL",
        "https://api.example.com/v1, https://api.example.com/v10/deployments, outside the base URL",
        "https://api.example.com/v1, https://user@api.example.com/v1/deployments, outside the base URL",
        "https://api.example.com/v1, https://api.example.com/v1/../admin, segment is not accepted",
        "https://api.example.com/v1, https://api.example.com/v1/deployments#x, fragment",
        "https://api.example.com/v1, /v1/../admin, segment is not accepted",
        "https://api.example.com/v1, //other.example.com/v1, absolute URL",
        "https://api.example.com/v1, ftp://api.example.com/v1/x, outside the base URL",
    )
    fun `Status URL outside the base URL`(base: String, statusUrl: String, reason: String) {
        val ex = assertFailsWith<HttpCallUrlException> {
            resolveHttpCallStatusUrl(base, statusUrl)
        }
        assertTrue(ex.message!!.contains(reason), ex.message)
    }
}
