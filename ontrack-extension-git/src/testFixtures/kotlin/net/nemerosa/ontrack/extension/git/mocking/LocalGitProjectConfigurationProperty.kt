package net.nemerosa.ontrack.extension.git.mocking

/**
 * Associates a project with a local Git repository, for testing.
 *
 * @property name Name of the configuration
 * @property remote Remote of the repository, like `file:///path/to/repo`
 * @property issueServiceConfigurationIdentifier Identifier of the issue service, if any
 */
data class LocalGitProjectConfigurationProperty(
    val name: String,
    val remote: String,
    val issueServiceConfigurationIdentifier: String?,
)
