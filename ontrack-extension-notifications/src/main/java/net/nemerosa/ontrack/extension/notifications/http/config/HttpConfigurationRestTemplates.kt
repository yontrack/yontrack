package net.nemerosa.ontrack.extension.notifications.http.config

import net.nemerosa.ontrack.extension.support.client.restTemplateBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpRedirects
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.time.Duration

/**
 * Creates a `RestTemplate` for the calls made under an HTTP configuration:
 *
 * - its authentication headers are set on every request;
 * - redirects are **not** followed, since they could carry the credentials to another host: a 3xx is
 *   returned as it is;
 * - no status is turned into an exception: the caller decides what an answer means;
 * - it goes through the JDK's `HttpURLConnection`, which honours the JVM proxy settings;
 * - it is built from [restTemplateBuilder] (ADR 0016).
 */
fun createHttpConfigurationRestTemplate(
    configuration: HttpConfiguration,
    connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    readTimeout: Duration = DEFAULT_READ_TIMEOUT,
): RestTemplate {
    val headers = configuration.authentication.headers()
    return restTemplateBuilder()
        .requestFactoryBuilder(ClientHttpRequestFactoryBuilder.simple())
        .redirects(HttpRedirects.DONT_FOLLOW)
        .connectTimeout(connectTimeout)
        .readTimeout(readTimeout)
        .errorHandler(NoErrorResponseHandler)
        .additionalInterceptors(ClientHttpRequestInterceptor { request, body, execution ->
            headers.forEach { (name, value) ->
                request.headers.set(name, value)
            }
            execution.execute(request, body)
        })
        .build()
}

private val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
private val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(30)

private object NoErrorResponseHandler : ResponseErrorHandler {
    override fun hasError(response: ClientHttpResponse): Boolean = false

    override fun handleError(url: URI, method: HttpMethod, response: ClientHttpResponse) {
    }
}
