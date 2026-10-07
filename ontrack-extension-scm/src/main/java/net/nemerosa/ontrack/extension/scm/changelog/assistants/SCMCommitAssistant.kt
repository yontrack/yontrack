package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Assistant (agent kind) which helped write a commit, as recognised from the markers git carries:
 * its trailers, its author and its committer.
 *
 * It is read from git every time a change log is computed, and never stored.
 *
 * @property name Name of the assistant
 * @property markers Markers which named this assistant on the commit, in the order they were found
 * @property sessionLink Link to the agent session behind the commit, when a trailer carries one
 */
@APIDescription("Assistant (agent kind) which helped write a commit, as recognised from its trailers, its author and its committer.")
data class SCMCommitAssistant(
    @APIDescription("Name of the assistant, e.g. Claude Code, Codex, Copilot, Devin, or the name of an agent marker pattern.")
    val name: String,
    @APIDescription("Markers which named this assistant on the commit, in the order they were found. An assistant appears once per commit, with all its markers.")
    val markers: List<SCMCommitAssistantMarker>,
    @APIDescription("Link to the agent session behind the commit, when a trailer carries one (e.g. Claude-Session). The first one found wins.")
    val sessionLink: String?,
)

/**
 * Kind of marker which names an assistant on a commit.
 */
enum class SCMCommitAssistantMarker {
    /**
     * A `Co-Authored-By` trailer whose email is an agent's.
     */
    CO_AUTHOR,

    /**
     * An `Assisted-by` trailer, naming the assistant.
     */
    ASSISTED_BY,

    /**
     * A session trailer (`Claude-Session`), carrying the session link.
     */
    SESSION_TRAILER,

    /**
     * A trailer whose key is named by an agent marker pattern.
     */
    TRAILER,

    /**
     * The author of the commit: its email or its login.
     */
    AUTHOR,

    /**
     * The committer of the commit: its login.
     */
    COMMITTER,
}
