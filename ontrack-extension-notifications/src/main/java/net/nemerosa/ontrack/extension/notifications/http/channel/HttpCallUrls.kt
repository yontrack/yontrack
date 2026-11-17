package net.nemerosa.ontrack.extension.notifications.http.channel

import java.net.URI
import java.net.URISyntaxException

private val absoluteUrl = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

/**
 * Resolves the path of a call, once templated, under the base URL of an HTTP configuration.
 *
 * The path, with its optional query, is appended to the base URL. The result must stay under the
 * base URL, since the call carries the credentials of the configuration: absolute URLs, network-path
 * references (`//host`), `.` and `..` segments (in clear or encoded), backslashes and fragments are
 * refused.
 *
 * @param baseUrl Base URL of the HTTP configuration
 * @param path Templated path, with an optional query
 * @return URL to call
 * @throws HttpCallUrlException If the path cannot be called under the base URL
 */
fun resolveHttpCallUrl(baseUrl: String, path: String): URI {
    fun refuse(reason: String): Nothing = throw HttpCallUrlException(baseUrl, path, reason)

    val trimmed = path.trim()
    if (absoluteUrl.containsMatchIn(trimmed) || trimmed.startsWith("//")) {
        refuse("an absolute URL is not accepted, give a path under the base URL")
    }
    if (trimmed.contains('\\')) {
        refuse("a backslash is not accepted")
    }

    val candidateText = when {
        trimmed.isEmpty() -> baseUrl
        trimmed.startsWith("?") -> baseUrl.trimEnd('/') + trimmed
        else -> baseUrl.trimEnd('/') + "/" + trimmed.trimStart('/')
    }
    val candidate = try {
        URI(candidateText)
    } catch (_: URISyntaxException) {
        refuse("it is not a valid URL, encode its special characters")
    }
    if (candidate.rawFragment != null) {
        refuse("a fragment is never sent")
    }
    // Dot segments, in clear or encoded: the decoded path is checked
    val segments = (candidate.path ?: "").split('/')
    if (segments.any { it == "." || it == ".." }) {
        refuse("a . or .. segment is not accepted")
    }

    // Defensive: the URL was built from the base URL, so it should not have left it
    val base = URI(baseUrl)
    val basePath = (base.rawPath ?: "").trimEnd('/')
    val candidatePath = candidate.rawPath ?: ""
    val sameOrigin = candidate.scheme.equals(base.scheme, ignoreCase = true) &&
            candidate.host.equals(base.host, ignoreCase = true) &&
            candidate.port == base.port &&
            candidate.rawUserInfo == null
    val underBasePath = basePath.isEmpty() || candidatePath == basePath || candidatePath.startsWith("$basePath/")
    if (!sameOrigin || !underBasePath) {
        refuse("it is outside the base URL")
    }

    return candidate
}
