package net.nemerosa.ontrack.extension.scm.service

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Configuration of the `scmCommit` templating source.
 */
data class SCMCommitTemplatingSourceConfig(
    @APIDescription("Field of the commit to render: `id` (the default) for the full hash of the commit, or `assistants` for the comma-separated names of the assistants (agent kinds) which helped write it, empty when there is none.")
    val field: String? = null,
)
