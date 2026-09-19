package net.nemerosa.ontrack.extension.gitlab.client

import net.nemerosa.ontrack.extension.gitlab.model.GitLabCommit
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.model.GitLabIssue
import net.nemerosa.ontrack.extension.gitlab.model.GitLabMergeRequest
import net.nemerosa.ontrack.extension.gitlab.model.GitLabProject
import net.nemerosa.ontrack.extension.gitlab.model.GitLabUser
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * GitLab client over Spring's [RestTemplate], for both gitlab.com and self-managed instances.
 *
 * It replaces the `gitlab4j-api` client, and with it the whole Jersey stack. Beyond reading like the other
 * SCM clients and being testable with `MockRestServiceServer`, it puts three things under explicit control:
 *
 * * **proxies** - the JDK's `HttpURLConnection` honours `http.proxyHost` and friends, which Jersey ignored
 *   (issue #588);
 * * **rate limits** - a 429 is retried after the delay `Retry-After` or `RateLimit-Reset` names;
 * * **pagination** - GitLab returns no total on several endpoints, so the pages are followed through the
 *   `Link` header rather than counted.
 *
 * @property configuration Configuration to connect with
 * @property sleeper How to wait between two attempts, in seconds. Replaced in tests.
 * @property epochSeconds Current time in epoch seconds, used to read `RateLimit-Reset`. Replaced in tests.
 */
class DefaultGitLabClient(
    private val configuration: GitLabConfiguration,
    private val sleeper: (seconds: Long) -> Unit = { Thread.sleep(it * 1000L) },
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) : GitLabClient {

    companion object {

        /**
         * GitLab's documented header for a personal access token.
         */
        const val PRIVATE_TOKEN_HEADER = "PRIVATE-TOKEN"

        const val API_PATH = "/api/v4"

        /**
         * Maximum page size GitLab accepts.
         */
        const val PAGE_SIZE = 100

        /**
         * A runaway `Link` chain is a bug, not a big instance: stop rather than page forever.
         */
        const val MAX_PAGES = 1000

        /**
         * How many times a 429 is waited out before giving up.
         */
        const val MAX_RETRIES = 3

        /**
         * Waited when a 429 carries no `Retry-After` and no `RateLimit-Reset`.
         */
        const val DEFAULT_RETRY_SECONDS = 10L

        /**
         * A `RateLimit-Reset` far in the future is not worth blocking a job thread for.
         */
        const val MAX_RETRY_SECONDS = 60L

        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(30)

        private val NEXT_LINK = Regex("""<([^>]+)>\s*;\s*rel\s*=\s*"next"""", RegexOption.IGNORE_CASE)

        /**
         * URL-encodes the full path of a project so that it can be used wherever GitLab expects an `:id`.
         *
         * Every `/` becomes `%2F`, subgroups included.
         */
        fun encodeProjectPath(path: String): String =
            URLEncoder.encode(path.trim('/'), StandardCharsets.UTF_8).replace("+", "%20")

        /**
         * URL-encodes the value of a query parameter.
         *
         * The URIs are built from already-encoded components, so a value carrying a `#` - an issue
         * reference, say - has to arrive encoded or it would be read as the start of a fragment.
         */
        fun encodeQueryValue(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

        /**
         * How long to wait after a 429, from the headers GitLab sent.
         */
        fun retryAfterSeconds(headers: HttpHeaders?, now: Long): Long {
            val retryAfter = headers?.getFirst(HttpHeaders.RETRY_AFTER)?.trim()?.toLongOrNull()
            val reset = headers?.getFirst("RateLimit-Reset")?.trim()?.toLongOrNull()?.let { it - now }
            val seconds = retryAfter ?: reset ?: DEFAULT_RETRY_SECONDS
            return seconds.coerceIn(1L, MAX_RETRY_SECONDS)
        }
    }

    override fun validate() {
        getForObject<GitLabUser>(uri("/user"))
    }

    override fun getProjects(): List<GitLabProject> =
        paginate(
            uri(
                "/projects",
                "membership" to "true",
                "simple" to "true",
                "order_by" to "path",
                "sort" to "asc",
            ),
            object : ParameterizedTypeReference<List<GitLabProject>>() {}
        )

    override fun getIssue(project: String, iid: Int): GitLabIssue? =
        notFoundAsNull {
            getForObject<GitLabIssue>(projectUri(project, "issues/$iid"))
        }

    override fun getMergeRequest(project: String, iid: Int): GitLabMergeRequest? =
        notFoundAsNull {
            getForObject<GitLabMergeRequest>(projectUri(project, "merge_requests/$iid"))
        }

    override fun getIssueLastCommit(project: String, iid: Int): String? =
        notFoundAsNull {
            val commits = getForList(
                projectUri(
                    project,
                    "search",
                    "scope" to "commits",
                    "search" to encodeQueryValue("#$iid"),
                    "per_page" to PAGE_SIZE.toString(),
                ),
                object : ParameterizedTypeReference<List<GitLabCommit>>() {}
            )
            // The search is a substring one and its order is GitLab's, so both the false positives and the
            // ordering are settled here rather than trusted.
            commits
                .filter { it.mentionsIssue(iid) }
                .maxWithOrNull(compareBy(nullsFirst()) { it.committedTime })
                ?.id
        }

    /**
     * Root of the API, with a single separator whatever the configuration's URL ends with.
     */
    private val apiRoot: String get() = configuration.url.trimEnd('/') + API_PATH

    private fun uri(path: String, vararg query: Pair<String, String>): URI =
        UriComponentsBuilder.fromUriString(apiRoot + path)
            .apply { query.forEach { (name, value) -> queryParam(name, value) } }
            .build(true)
            .toUri()

    /**
     * URI of a resource of a project, whose full path is URL-encoded into a single path segment.
     */
    private fun projectUri(project: String, path: String, vararg query: Pair<String, String>): URI =
        uri("/projects/${encodeProjectPath(project)}/$path", *query)

    private inline fun <reified T : Any> getForObject(uri: URI): T =
        withRateLimit { template.getForObject(uri, T::class.java) } ?: throw GitLabNoResponseException(uri.toString())

    /**
     * A single page of a list endpoint, for the calls which only ever want the first one.
     */
    private fun <T> getForList(uri: URI, type: ParameterizedTypeReference<List<T>>): List<T> =
        withRateLimit { template.exchange(uri, HttpMethod.GET, null, type) }.body ?: emptyList()

    /**
     * Items of every page, following the `Link` header GitLab sends: several endpoints return no total at
     * all, so nothing here counts pages from a total.
     */
    private fun <T> paginate(first: URI, type: ParameterizedTypeReference<List<T>>): List<T> {
        val results = mutableListOf<T>()
        var next: URI? = UriComponentsBuilder.fromUri(first)
            .queryParam("per_page", PAGE_SIZE)
            .queryParam("page", 1)
            .build(true)
            .toUri()
        var pages = 0
        while (next != null && pages < MAX_PAGES) {
            val response: ResponseEntity<List<T>> =
                withRateLimit { template.exchange(next!!, HttpMethod.GET, null, type) }
            results += response.body ?: emptyList()
            next = nextPage(response.headers)
            pages++
        }
        return results
    }

    private fun nextPage(headers: HttpHeaders): URI? =
        headers[HttpHeaders.LINK]
            ?.firstNotNullOfOrNull { NEXT_LINK.find(it)?.groupValues?.get(1) }
            ?.let { URI.create(it) }

    /**
     * Runs [code], waiting out a 429 as long as GitLab keeps asking for it.
     */
    private fun <T> withRateLimit(code: () -> T): T {
        var attempts = 0
        while (true) {
            try {
                return code()
            } catch (ex: HttpClientErrorException.TooManyRequests) {
                if (attempts >= MAX_RETRIES) {
                    throw GitLabRateLimitException(attempts)
                }
                attempts++
                sleeper(retryAfterSeconds(ex.responseHeaders, epochSeconds()))
            }
        }
    }

    private fun <T> notFoundAsNull(code: () -> T): T? =
        try {
            code()
        } catch (_: HttpClientErrorException.NotFound) {
            null
        }

    internal val template: RestTemplate by lazy {
        val token = configuration.token?.takeIf { it.isNotBlank() }
            ?: error("GitLab configuration ${configuration.name} has no token.")
        RestTemplate(requestFactory()).apply {
            interceptors = listOf(
                ClientHttpRequestInterceptor { request, body, execution ->
                    request.headers.set(PRIVATE_TOKEN_HEADER, token)
                    execution.execute(request, body)
                }
            )
        }
    }

    /**
     * `SimpleClientHttpRequestFactory` goes through the JDK's `HttpURLConnection`, which reads the JVM proxy
     * settings - the whole point of issue #588 - and is where an ignored SSL certificate is arranged.
     */
    private fun requestFactory(): ClientHttpRequestFactory {
        val factory = if (configuration.ignoreSslCertificate) {
            object : SimpleClientHttpRequestFactory() {
                override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
                    if (connection is HttpsURLConnection) {
                        connection.sslSocketFactory = trustAllSslContext().socketFactory
                        connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
                    }
                    super.prepareConnection(connection, httpMethod)
                }
            }
        } else {
            SimpleClientHttpRequestFactory()
        }
        factory.setConnectTimeout(CONNECT_TIMEOUT)
        factory.setReadTimeout(READ_TIMEOUT)
        return factory
    }

    private fun trustAllSslContext(): SSLContext {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAll), SecureRandom())
        }
    }
}
