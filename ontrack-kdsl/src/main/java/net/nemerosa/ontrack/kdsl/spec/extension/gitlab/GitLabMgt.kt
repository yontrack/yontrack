package net.nemerosa.ontrack.kdsl.spec.extension.gitlab

import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.spec.Ontrack

/**
 * GitLab management.
 */
class GitLabMgt(connector: Connector) : Connected(connector)

val Ontrack.gitLab: GitLabMgt get() = GitLabMgt(connector)
