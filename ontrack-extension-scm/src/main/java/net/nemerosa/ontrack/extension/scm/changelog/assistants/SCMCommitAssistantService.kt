package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit

/**
 * Recognises the assistants (agent kinds) which helped write a commit, using the
 * [agent markers settings][AgentMarkersSettings].
 */
interface SCMCommitAssistantService {

    /**
     * Assistants of a commit.
     *
     * @param commit Commit to read
     * @return Assistants of the commit, once each, in the order they were found
     */
    fun getAssistants(commit: SCMCommit): List<SCMCommitAssistant>

}
