package net.nemerosa.ontrack.extension.gitlab.config

import java.net.URI

/**
 * Matching of a Git clone URL against the URL of a configured GitLab instance.
 *
 * GitLab is supported on gitlab.com **and** self-managed, so - unlike Bitbucket Cloud - the host cannot be
 * hardcoded: a SCM URL belongs to this engine when its host is the host of one of the configured instances.
 *
 * The comparison is a **host equality**, never a substring: `https://gitlab.com.attacker.example/...`,
 * `https://gitlab.common.example/...`, `https://attacker.example/gitlab.com/...` and
 * `https://gitlab.com@attacker.example/...` all name another host and none of them matches a configuration
 * on `https://gitlab.com`.
 *
 * The **port and the scheme are deliberately ignored**: the configuration URL is the HTTPS API endpoint while
 * a clone URL is just as legitimately SSH on another port, and both name the same deployment.
 *
 * Everything after the host is the project path - GitLab subgroups nest arbitrarily deep, so all of it is
 * taken, with a trailing `.git` stripped. A project always lives in a namespace, so a path of less than two
 * segments is not one.
 */
object GitLabSCMUrl {

    /**
     * Project path of [scmUrl] on the GitLab instance at [configurationUrl], or `null` when the URL does not
     * belong to that instance.
     */
    fun projectPath(configurationUrl: String, scmUrl: String): String? {
        val instance = parseInstanceUrl(configurationUrl) ?: return null
        val scm = parseScmUrl(scmUrl) ?: return null
        if (!scm.host.equals(instance.host, ignoreCase = true)) return null
        val path = when {
            // The instance is at the root of its host
            instance.path.isEmpty() -> scm.path
            // The instance is under a relative URL root, and the SCM URL carries it
            scm.path.startsWith("${instance.path}/") -> scm.path.removePrefix("${instance.path}/")
            // ... but a SSH clone URL never does, GitLab's relative URL root being a HTTP concern only
            scm.scp -> scm.path
            // Same host, another application
            else -> return null
        }
        val projectPath = path.trim('/').removeSuffix(".git").trim('/')
        val segments = projectPath.split("/")
        return if (segments.size >= 2 && segments.all { it.isNotBlank() }) {
            projectPath
        } else {
            null
        }
    }

    private fun parseInstanceUrl(url: String): ParsedUrl? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        return ParsedUrl(host = host, path = (uri.path ?: "").trim('/'), scp = false)
    }

    private fun parseScmUrl(url: String): ParsedUrl? {
        val trimmed = url.trim()
        return if (trimmed.contains("://")) {
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val host = uri.host ?: return null
            ParsedUrl(host = host, path = (uri.path ?: "").trim('/'), scp = false)
        } else {
            val m = SCP_REGEX.matchEntire(trimmed) ?: return null
            ParsedUrl(host = m.groupValues[1], path = m.groupValues[2].trim('/'), scp = true)
        }
    }

    private data class ParsedUrl(
        val host: String,
        val path: String,
        /**
         * `true` for the SCP-like SSH form, `git@host:group/project.git`.
         */
        val scp: Boolean,
    )

    /**
     * SCP-like SSH form, `[user@]host:path`. Checked only after the `scheme://` forms have been ruled out.
     */
    private val SCP_REGEX = "^(?:[^@/\\s]+@)?([^@/:\\s]+):(.+)$".toRegex()
}
