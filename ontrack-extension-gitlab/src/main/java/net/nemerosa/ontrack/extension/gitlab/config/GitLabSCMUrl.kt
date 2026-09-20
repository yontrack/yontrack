package net.nemerosa.ontrack.extension.gitlab.config

import net.nemerosa.ontrack.extension.scm.support.SCMUrl

/**
 * Matching of a Git clone URL against the URL of a configured GitLab instance.
 *
 * GitLab is supported on gitlab.com **and** self-managed, so - unlike Bitbucket Cloud - the host cannot be
 * hardcoded: a SCM URL belongs to this engine when its host is the host of one of the configured instances.
 *
 * The comparison is a **host equality**, never a substring - see [SCMUrl], which this object shares with the
 * GitHub and Bitbucket Server configurations.
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
        val instance = SCMUrl.parseConfigurationUrl(configurationUrl) ?: return null
        val scm = SCMUrl.parseScmUrl(scmUrl) ?: return null
        if (!SCMUrl.sameHost(instance, scm)) return null
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
}
