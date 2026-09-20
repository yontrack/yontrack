package net.nemerosa.ontrack.extension.scm.support

import java.net.URI

/**
 * Parsing of Git clone URLs and of the URLs of configured SCM instances, and matching of the former against
 * the latter.
 *
 * A clone URL belongs to a configured SCM instance when it names the **same host**, compared for equality,
 * without case. A substring test is never an origin comparison: for a configuration on `https://github.com`,
 * `https://github.com.attacker.example/a/b.git`, `https://github.common.example/a/b.git`,
 * `https://attacker.example/github.com/a/b.git` and `https://github.com@attacker.example/a/b.git` all name
 * another host, and none of them matches. Since the name of a configuration is a reference to a stored
 * credential, a URL matching a configuration it does not belong to points later SCM operations - change log,
 * file download, auto-versioning - at a foreign host, authenticated with that credential.
 *
 * The **scheme and the port are deliberately ignored**: the configuration URL is the HTTPS API endpoint while
 * a clone URL is just as legitimately `ssh://host:7999/...`, and both name the same deployment. The **path of
 * the configuration URL is ignored** as well, for the same reason: a SSH clone URL does not carry the context
 * path under which the SCM is served.
 */
object SCMUrl {

    /**
     * Checks whether the clone URL [scmUrl] belongs to the SCM instance configured at [configurationUrl].
     *
     * The two arguments are not interchangeable: [configurationUrl] is an API endpoint and is parsed by
     * [parseConfigurationUrl], [scmUrl] is a clone URL and is parsed by [parseScmUrl].
     */
    fun sameHost(configurationUrl: String, scmUrl: String): Boolean {
        val configuration = parseConfigurationUrl(configurationUrl) ?: return false
        val scm = parseScmUrl(scmUrl) ?: return false
        return sameHost(configuration, scm)
    }

    /**
     * Checks whether two parsed URLs name the same host, for callers which need the parsed parts anyway.
     */
    fun sameHost(configuration: SCMUrlParts, scm: SCMUrlParts): Boolean =
        scm.host.equals(configuration.host, ignoreCase = true)

    /**
     * Parses the URL of a configured SCM instance, `null` when it does not name a host.
     *
     * The URL of a configuration is an API endpoint, always a `scheme://host[/path]` URL - never the SCP-like
     * SSH form, which [parseScmUrl] accepts for clone URLs.
     */
    fun parseConfigurationUrl(url: String): SCMUrlParts? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = hostOf(uri) ?: return null
        return SCMUrlParts(host = host, path = (uri.path ?: "").trim('/'), scp = false)
    }

    /**
     * Parses a Git clone URL, `null` when it does not name a host.
     *
     * Both the `scheme://[user@]host[:port]/path` form and the SCP-like `[user@]host:path` one are accepted.
     */
    fun parseScmUrl(url: String): SCMUrlParts? {
        val trimmed = url.trim()
        return if (trimmed.contains("://")) {
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val host = hostOf(uri) ?: return null
            SCMUrlParts(host = host, path = (uri.path ?: "").trim('/'), scp = false)
        } else {
            val m = SCP_REGEX.matchEntire(trimmed) ?: return null
            SCMUrlParts(host = m.groupValues[1], path = m.groupValues[2].trim('/'), scp = true)
        }
    }

    /**
     * Host named by [uri].
     *
     * [URI.getHost] is `null` for any authority the RFC does not allow as a server-based one - a host name
     * with an underscore, common enough on internal networks, is the usual case. The host is then the
     * authority, less its user information and its port; taking it from the authority rather than giving up
     * keeps such an instance matching, and the comparison an equality.
     */
    private fun hostOf(uri: URI): String? {
        uri.host?.let { return it }
        val authority = uri.authority?.substringAfterLast('@') ?: return null
        val host = if (authority.startsWith("[")) {
            // IPv6 literal, `[::1]`, whose colons are not a port separator
            authority.substringBefore(']') + "]"
        } else {
            authority.substringBefore(':')
        }
        return host.takeIf { it.isNotBlank() }
    }

    /**
     * SCP-like SSH form, `[user@]host:path`. Checked only after the `scheme://` forms have been ruled out.
     */
    private val SCP_REGEX = "^(?:[^@/\\s]+@)?([^@/:\\s]+):(.+)$".toRegex()
}

/**
 * Host and path of a parsed SCM URL.
 *
 * @property host Host named by the URL
 * @property path Path of the URL, without its leading and trailing slashes
 * @property scp `true` for the SCP-like SSH form, `git@host:path`
 */
data class SCMUrlParts(
    val host: String,
    val path: String,
    val scp: Boolean,
)
