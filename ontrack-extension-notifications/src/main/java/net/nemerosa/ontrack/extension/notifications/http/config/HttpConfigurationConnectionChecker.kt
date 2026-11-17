package net.nemerosa.ontrack.extension.notifications.http.config

import net.nemerosa.ontrack.model.support.ConnectionResult
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import java.net.URI

/**
 * Connection test of an HTTP configuration: a `GET` on its base URL, with its authentication.
 *
 * It is OK unless the call fails (transport error), is refused (401, 403), fails on the server (5xx)
 * or is redirected (3xx, never followed). A 404 is OK: an API root often has nothing to show.
 */
@Component
class HttpConfigurationConnectionChecker {

    fun check(configuration: HttpConfiguration): ConnectionResult {
        val restTemplate = createHttpConfigurationRestTemplate(configuration)
        return try {
            restTemplate.execute(URI(configuration.url), HttpMethod.GET, null) { response ->
                val status = response.statusCode
                val code = status.value()
                when {
                    status.is3xxRedirection -> ConnectionResult.error(
                        "$code ${reason(code)}: redirected to ${response.headers.getFirst(HttpHeaders.LOCATION)}, which is not followed."
                    )

                    code == 401 || code == 403 -> ConnectionResult.error(
                        "$code ${reason(code)}: the authentication was refused."
                    )

                    status.is5xxServerError -> ConnectionResult.error(
                        "$code ${reason(code)}: server error."
                    )

                    else -> ConnectionResult.ok()
                }
            } ?: ConnectionResult.error("No response from ${configuration.url}")
        } catch (any: RestClientException) {
            ConnectionResult.error(any)
        }
    }

    private fun reason(code: Int): String = HttpStatus.resolve(code)?.reasonPhrase ?: ""

}
