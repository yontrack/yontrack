package net.nemerosa.ontrack.kdsl.spec.extension.gitlab

import net.nemerosa.ontrack.kdsl.spec.configurations.ConfigurationInterface
import net.nemerosa.ontrack.kdsl.spec.configurations.ConfigurationsMgt

/**
 * Management of the GitLab configurations (create, find, delete).
 */
val ConfigurationsMgt.gitLab: ConfigurationInterface<GitLabConfiguration>
    get() = ConfigurationInterface(
        connector = connector,
        id = "gitlab",
        type = GitLabConfiguration::class,
    )
