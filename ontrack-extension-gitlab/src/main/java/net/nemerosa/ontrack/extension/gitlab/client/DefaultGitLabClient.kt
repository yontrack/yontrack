package net.nemerosa.ontrack.extension.gitlab.client

import net.nemerosa.ontrack.extension.support.client.jackson2ClientMessageConverters
import net.nemerosa.ontrack.extension.gitlab.model.GitLabBranch
import net.nemerosa.ontrack.extension.gitlab.model.GitLabCommit
import net.nemerosa.ontrack.extension.gitlab.model.GitLabCompare
import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.model.GitLabFile
import net.nemerosa.ontrack.extension.gitlab.model.GitLabIssue
import net.nemerosa.ontrack.extension.gitlab.model.GitLabMergeRequest
import net.nemerosa.ontrack.extension.gitlab.model.GitLabPipeline
import net.nemerosa.ontrack.extension.gitlab.model.GitLabProject
import net.nemerosa.ontrack.extension.gitlab.model.GitLabUser
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.DefaultResponseErrorHandler
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
import java.util.Base64
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

        /**
         * How many times a file which is not found is read again when the caller asked to retry. Aligned
         * with GitHub's own `notFoundRetries`.
         */
        const val NOT_FOUND_RETRIES = 6

        /**
         * Waited between two reads of a file which is not found. Aligned with GitHub's `notFoundInterval`.
         */
        const val NOT_FOUND_RETRY_SECONDS = 5L

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
         * URL-encodes a value which must sit inside a **single** path segment - a branch name, a file path.
         *
         * Every `/` becomes `%2F`, which is how GitLab expects a branch like `feature/one` or a file like
         * `src/main/app.yaml` to be named in a URL. Unlike [encodeProjectPath] the outer slashes are kept:
         * a file path is not a project path and trimming them would change which file is named.
         */
        fun encodePathSegment(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

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
        getCurrentUser()
    }

    override fun getCurrentUser(): GitLabUser = getForObject<GitLabUser>(uri("/user"))

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

    override fun getProject(project: String): GitLabProject? =
        notFoundAsNull {
            getForObject<GitLabProject>(uri("/projects/${encodeProjectPath(project)}"))
        }

    override fun getBranch(project: String, branch: String): GitLabBranch? =
        notFoundAsNull {
            getForObject<GitLabBranch>(projectUri(project, "repository/branches/${encodePathSegment(branch)}"))
        }

    override fun getBranchLastCommit(project: String, branch: String): String? =
        getBranch(project, branch)?.commit?.id

    override fun createBranch(project: String, sourceBranch: String, newBranch: String): String {
        val created: GitLabBranch = withRateLimit {
            template.exchange(
                projectUri(
                    project,
                    "repository/branches",
                    "branch" to encodeQueryValue(newBranch),
                    "ref" to encodeQueryValue(sourceBranch),
                ),
                HttpMethod.POST,
                authEntity(),
                GitLabBranch::class.java,
            )
        }.body ?: throw GitLabCannotCreateBranchException(project, newBranch, sourceBranch)
        return created.commit?.id ?: throw GitLabCannotCreateBranchException(project, newBranch, sourceBranch)
    }

    override fun deleteBranch(project: String, branch: String) {
        notFoundAsNull {
            withRateLimit {
                template.exchange(
                    projectUri(project, "repository/branches/${encodePathSegment(branch)}"),
                    HttpMethod.DELETE,
                    authEntity(),
                    Void::class.java,
                )
            }
        }
    }

    override fun download(project: String, ref: String, path: String, retryOnNotFound: Boolean): ByteArray? {
        if (!retryOnNotFound) return downloadOnce(project, ref, path)
        var attempts = 0
        while (true) {
            downloadOnce(project, ref, path)?.let { return it }
            attempts++
            if (attempts >= NOT_FOUND_RETRIES) return null
            sleeper(NOT_FOUND_RETRY_SECONDS)
        }
    }

    private fun downloadOnce(project: String, ref: String, path: String): ByteArray? =
        notFoundAsNull {
            withRateLimit {
                template.exchange(
                    projectUri(
                        project,
                        "repository/files/${encodePathSegment(path.trimStart('/'))}/raw",
                        "ref" to encodeQueryValue(ref),
                    ),
                    HttpMethod.GET,
                    authEntity(),
                    ByteArray::class.java,
                )
            }.body
        }

    /**
     * GitLab has one endpoint for the file, `POST` to create it and `PUT` to replace it, and answers 400
     * when the verb does not match what the repository holds.
     *
     * The file is therefore **read first**, which settles two things in one request. It says which of the
     * two verbs to use, rather than trying one and reading a bare 400 as "the other one then" - a 400 that
     * actually meant a bad branch or an empty commit message used to be retried as a creation and fail
     * twice. And it gives the file's `last_commit_id`, which the update sends back so that GitLab rejects
     * the write when the file moved in between: auto-versioning is exactly the workload where two orders
     * can be writing the same file at the same time, and without it the later write wins silently.
     *
     * Note that the [SCM][net.nemerosa.ontrack.extension.scm.service.SCM] interface's own `commit` argument
     * cannot serve here: it is the head of the upgrade branch, while GitLab's `last_commit_id` is the last
     * commit **of that file**, which is almost never the same commit.
     */
    override fun upload(project: String, branch: String, path: String, content: ByteArray, message: String) {
        val filePath = encodePathSegment(path.trimStart('/'))
        val existing = getFile(project, branch, path)
        val uri = projectUri(project, "repository/files/$filePath")
        val body = buildMap {
            put("branch", branch)
            put("content", Base64.getEncoder().encodeToString(content))
            put("encoding", "base64")
            put("commit_message", message)
            existing?.last_commit_id?.takeIf { it.isNotBlank() }?.let { put("last_commit_id", it) }
        }
        val method = if (existing != null) HttpMethod.PUT else HttpMethod.POST
        withRateLimit { template.exchange(uri, method, authEntity(body), Void::class.java) }
    }

    /**
     * Metadata of a file - its `last_commit_id` above all - or `null` when the file is not there.
     */
    private fun getFile(project: String, ref: String, path: String): GitLabFile? =
        notFoundAsNull {
            getForObject<GitLabFile>(
                projectUri(
                    project,
                    "repository/files/${encodePathSegment(path.trimStart('/'))}",
                    "ref" to encodeQueryValue(ref),
                )
            )
        }

    override fun createMergeRequest(
        project: String,
        sourceBranch: String,
        targetBranch: String,
        title: String,
        description: String,
        reviewerIds: List<Long>,
        removeSourceBranch: Boolean,
        squash: Boolean,
    ): GitLabMergeRequest {
        val body = buildMap<String, Any> {
            put("source_branch", sourceBranch)
            put("target_branch", targetBranch)
            put("title", title)
            put("description", description)
            put("remove_source_branch", removeSourceBranch)
            put("squash", squash)
            if (reviewerIds.isNotEmpty()) put("reviewer_ids", reviewerIds)
        }
        return withRateLimit {
            template.exchange(
                projectUri(project, "merge_requests"),
                HttpMethod.POST,
                authEntity(body),
                GitLabMergeRequest::class.java,
            )
        }.body ?: throw GitLabCannotCreateMergeRequestException(project, sourceBranch, targetBranch)
    }

    override fun approveMergeRequest(project: String, iid: Int) {
        withRateLimit {
            template.exchange(
                projectUri(project, "merge_requests/$iid/approve"),
                HttpMethod.POST,
                authEntity(emptyMap<String, Any>()),
                Void::class.java,
            )
        }
    }

    override fun mergeMergeRequest(
        project: String,
        iid: Int,
        sha: String,
        message: String,
        squash: Boolean,
        removeSourceBranch: Boolean,
        autoMerge: Boolean,
    ): GitLabMergeRequest {
        val body = buildMap<String, Any> {
            // Always sent: 19.2 can make it mandatory, and a mismatch is a 409 rather than a wrong merge
            put("sha", sha)
            put("merge_commit_message", message)
            put("squash", squash)
            if (squash) put("squash_commit_message", message)
            put("should_remove_source_branch", removeSourceBranch)
            // `auto_merge`, not `merge_when_pipeline_succeeds`, deprecated in 17.11
            if (autoMerge) put("auto_merge", true)
        }
        return withRateLimit {
            template.exchange(
                projectUri(project, "merge_requests/$iid/merge"),
                HttpMethod.PUT,
                authEntity(body),
                GitLabMergeRequest::class.java,
            )
        }.body ?: throw GitLabCannotMergeMergeRequestException(project, iid)
    }

    override fun findUserByUsername(username: String): GitLabUser? =
        notFoundAsNull {
            getForList(
                uri("/users", "username" to encodeQueryValue(username)),
                object : ParameterizedTypeReference<List<GitLabUser>>() {}
            ).firstOrNull { it.username.equals(username, ignoreCase = true) }
        }

    override fun getCommits(
        project: String,
        fromRef: String,
        toRef: String,
        maxCommits: Int,
    ): List<GitLabCommit> {
        val compare = notFoundAsNull {
            getForObject<GitLabCompare>(
                projectUri(
                    project,
                    "repository/compare",
                    "from" to encodeQueryValue(fromRef),
                    "to" to encodeQueryValue(toRef),
                    // `false` is GitLab's `from...to`: against the merge base rather than the plain diff
                    "straight" to "false",
                )
            )
        } ?: return emptyList()
        // GitLab returns the commits oldest first, while a change log reads most recent first
        return compare.commits.reversed().take(maxCommits.coerceAtLeast(0))
    }

    override fun getCommit(project: String, commit: String): GitLabCommit? =
        notFoundAsNull {
            getForObject<GitLabCommit>(projectUri(project, "repository/commits/${encodePathSegment(commit)}"))
        }

    /**
     * The variables go over as GitLab's array of hashes, `[{"key": "K", "value": "V"}]`, which is the only
     * form `POST /projects/:id/pipeline` accepts for them.
     *
     * Nothing of the configuration travels in that body: the personal access token is attached to the
     * request by [authEntity], as a header, and never as a variable.
     */
    override fun triggerPipeline(project: String, ref: String, variables: Map<String, String>): GitLabPipeline {
        val body = mapOf(
            "ref" to ref,
            "variables" to variables.map { (key, value) -> mapOf("key" to key, "value" to value) },
        )
        return withRateLimit {
            template.exchange(
                projectUri(project, "pipeline"),
                HttpMethod.POST,
                authEntity(body),
                GitLabPipeline::class.java,
            )
        }.body ?: throw GitLabCannotTriggerPipelineException(project, ref)
    }

    override fun getPipeline(project: String, pipelineId: Long): GitLabPipeline? =
        notFoundAsNull {
            getForObject<GitLabPipeline>(projectUri(project, "pipelines/$pipelineId"))
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
        withRateLimit { template.exchange(uri, HttpMethod.GET, authEntity(), T::class.java) }.body
            ?: throw GitLabNoResponseException(uri.toString())

    /**
     * A single page of a list endpoint, for the calls which only ever want the first one.
     */
    private fun <T> getForList(uri: URI, type: ParameterizedTypeReference<List<T>>): List<T> =
        withRateLimit { template.exchange(uri, HttpMethod.GET, authEntity(), type) }.body ?: emptyList()

    /**
     * Items of every page, following the `Link` header GitLab sends: several endpoints return no total at
     * all, so nothing here counts pages from a total.
     *
     * **Only the page number is taken from that header.** Every URL this loop calls is built here from
     * [first], which the client composed itself; the remote server chooses how far the paging goes and
     * nothing else of the request. See [nextPage].
     */
    private fun <T> paginate(first: URI, type: ParameterizedTypeReference<List<T>>): List<T> {
        val results = mutableListOf<T>()
        var page: Int? = 1
        var pages = 0
        while (page != null && pages < MAX_PAGES) {
            val current = page
            val response: ResponseEntity<List<T>> =
                withRateLimit { template.exchange(pageUri(first, current), HttpMethod.GET, authEntity(), type) }
            results += response.body ?: emptyList()
            // Strictly forward, so that a server repeating a page number cannot spin this loop
            page = nextPage(response.headers)?.takeIf { it > current }
            pages++
        }
        return results
    }

    /**
     * URI of one page of [first], the only shape of URL [paginate] ever calls.
     */
    private fun pageUri(first: URI, page: Int): URI =
        UriComponentsBuilder.fromUri(first)
            .replaceQueryParam("per_page", PAGE_SIZE)
            .replaceQueryParam("page", page)
            .build(true)
            .toUri()

    /**
     * Number of the next page, from the `Link` header of the previous response - **and nothing else of it**.
     *
     * The header is written by the remote server, and the personal access token travels on every request
     * this client makes: a `Link` pointing at another host would not merely fetch a foreign URL, it would
     * hand GitLab's token to whoever sent the header. So the target is never requested as it stands. It is
     * reduced to its `page` query parameter, an integer, which [paginate] applies to a URL it built itself.
     * Host, path, and every other parameter of the header are dropped, whatever they say.
     *
     * The origin check of [isOnInstance] stays in front of that as a second line: a `rel="next"` which is
     * not even on the configured instance is a broken or hostile answer, and its page number is worth no
     * more than the rest of it.
     *
     * A target carrying no usable `page` ends the pagination - which is also what GitLab's keyset
     * pagination would do, and no endpoint here asks for it.
     *
     * See the `java/ssrf` alerts https://github.com/yontrack/yontrack/security/code-scanning/355
     * and https://github.com/yontrack/yontrack/security/code-scanning/357.
     */
    private fun nextPage(headers: HttpHeaders): Int? =
        headers[HttpHeaders.LINK]
            ?.firstNotNullOfOrNull { NEXT_LINK.find(it)?.groupValues?.get(1) }
            ?.let { link ->
                val uri = try {
                    // Normalised first, so that a `..` cannot walk out of the API path past the check below
                    URI.create(link.trim()).normalize()
                } catch (_: IllegalArgumentException) {
                    null
                }
                uri?.takeIf { isOnInstance(it) }?.let { pageNumber(it) }
            }

    /**
     * The `page` query parameter of [uri], when it holds a usable one.
     */
    private fun pageNumber(uri: URI): Int? =
        uri.rawQuery
            ?.splitToSequence('&')
            ?.firstOrNull { it.substringBefore('=') == "page" }
            ?.substringAfter('=', "")
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

    /**
     * Is [uri] the same origin as the configured instance, and under the API path?
     *
     * The port is compared after defaulting it from the scheme, so that `https://gitlab.com` and
     * `https://gitlab.com:443` are the same origin and `https://gitlab.com:8443` is not.
     */
    internal fun isOnInstance(uri: URI): Boolean {
        val expected = try {
            URI.create(apiRoot)
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (!uri.isAbsolute || uri.host.isNullOrBlank()) return false
        // No credentials smuggled into the authority: the token is the only one this client sends
        if (uri.rawUserInfo != null) return false
        if (!uri.scheme.equals(expected.scheme, ignoreCase = true)) return false
        if (!uri.host.equals(expected.host, ignoreCase = true)) return false
        if (defaultedPort(uri) != defaultedPort(expected)) return false
        val path = uri.rawPath ?: return false
        val expectedPath = expected.rawPath ?: return false
        return path == expectedPath || path.startsWith(expectedPath.trimEnd('/') + "/")
    }

    private fun defaultedPort(uri: URI): Int =
        if (uri.port >= 0) {
            uri.port
        } else {
            when (uri.scheme?.lowercase()) {
                "https" -> 443
                "http" -> 80
                else -> -1
            }
        }

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

    /**
     * The personal access token, which a configuration must carry.
     */
    private fun token(): String = configuration.token?.takeIf { it.isNotBlank() }
        ?: error("GitLab configuration ${configuration.name} has no token.")

    /**
     * Headers carrying the token, built **per request** rather than installed as an interceptor on the
     * template.
     *
     * An interceptor stamps the credential on whatever URL the template is handed, this instance's or not.
     * Attaching it call by call keeps the token tied to the URIs this client builds itself from
     * [GitLabConfiguration.url]. Together with the origin check of [nextPage] it is the fix for the
     * `java/ssrf` alert https://github.com/yontrack/yontrack/security/code-scanning/355.
     */
    private fun authHeaders(): HttpHeaders = HttpHeaders().apply {
        set(PRIVATE_TOKEN_HEADER, token())
    }

    private fun authEntity(): HttpEntity<Void> = HttpEntity(authHeaders())

    private fun <T : Any> authEntity(body: T): HttpEntity<T> = HttpEntity(body, authHeaders())

    internal val template: RestTemplate by lazy {
        // Fails early, and on the configuration rather than on the first call
        token()
        RestTemplate(requestFactory()).apply {
            setMessageConverters(jackson2ClientMessageConverters())
            errorHandler = RedirectRejectingErrorHandler()
        }
    }

    /**
     * `SimpleClientHttpRequestFactory` goes through the JDK's `HttpURLConnection`, which reads the JVM proxy
     * settings - the whole point of issue #588 - and is where an ignored SSL certificate is arranged.
     *
     * It is also where **redirects are turned off**, which is the third leg of the `java/ssrf` fix. Left to
     * itself `prepareConnection` calls `setInstanceFollowRedirects(true)` for a GET, and `HttpURLConnection`
     * replays the request properties - the `PRIVATE-TOKEN` header among them - onto the redirected request,
     * stripping nothing. A 302 from a hostile or compromised response would therefore hand the personal
     * access token to an arbitrary host, exactly as a cross-host `Link` header would, by another route.
     * Nothing this client calls redirects: every GitLab API v4 endpoint it uses answers directly.
     */
    internal fun requestFactory(): SimpleClientHttpRequestFactory {
        val ignoreSsl = configuration.ignoreSslCertificate
        val factory = object : SimpleClientHttpRequestFactory() {
            override fun prepareConnection(connection: HttpURLConnection, httpMethod: String) {
                if (ignoreSsl && connection is HttpsURLConnection) {
                    connection.sslSocketFactory = trustAllSslContext().socketFactory
                    connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
                }
                super.prepareConnection(connection, httpMethod)
                // After the super call, which turns them back on for a GET
                connection.instanceFollowRedirects = false
            }
        }
        factory.setConnectTimeout(CONNECT_TIMEOUT)
        factory.setReadTimeout(READ_TIMEOUT)
        return factory
    }

    /**
     * Turns the redirect the client no longer follows into an error naming it, rather than letting it reach
     * the callers as an answer with no body.
     */
    private class RedirectRejectingErrorHandler : DefaultResponseErrorHandler() {

        override fun hasError(statusCode: HttpStatusCode): Boolean =
            statusCode.is3xxRedirection || super.hasError(statusCode)

        override fun handleError(url: URI, method: HttpMethod, response: ClientHttpResponse) {
            if (response.statusCode.is3xxRedirection) {
                throw GitLabRedirectException(
                    url.toString(),
                    response.headers.getFirst(HttpHeaders.LOCATION),
                )
            }
            super.handleError(url, method, response)
        }
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
