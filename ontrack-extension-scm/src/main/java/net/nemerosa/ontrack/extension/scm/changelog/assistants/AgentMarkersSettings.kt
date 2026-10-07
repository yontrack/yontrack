package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.annotations.APILabel

/**
 * Settings of the agent markers: how a change log recognises the assistants (agent kinds) which
 * helped write a commit.
 *
 * @property builtInConventions Whether the built-in conventions are recognised
 * @property patterns Additional patterns
 */
@APIDescription("Agent markers: how a change log recognises the assistants (agent kinds) which helped write a commit, from its trailers, its author and its committer.")
data class AgentMarkersSettings(
    @APILabel("Built-in conventions")
    @APIDescription("Recognises the built-in conventions: the Co-Authored-By trailers of Claude Code (noreply@anthropic.com), Codex (codex@openai.com) and Copilot (copilot@github.com), the Assisted-by and Claude-Session trailers, the author email copilot@github.com, and the logins copilot-swe-agent[bot] and devin-ai-integration[bot].")
    val builtInConventions: Boolean = true,
    @APILabel("Patterns")
    @APIDescription("Additional patterns, which add to the built-in conventions: how internal agents are recognised.")
    val patterns: List<AgentMarkerPattern> = emptyList(),
)

/**
 * Pattern recognising an assistant on a commit.
 *
 * @property name Name of the assistant to report
 * @property type What the [value] is matched against
 * @property value Regular expression, trailer key or login, depending on the [type]
 */
@APIDescription("Pattern recognising an assistant on a commit.")
data class AgentMarkerPattern(
    @APILabel("Name")
    @APIDescription("Name of the assistant to report.")
    val name: String,
    @APILabel("Type")
    @APIDescription("What the value is matched against: CO_AUTHOR_EMAIL (a regular expression matching the whole email of a Co-Authored-By trailer), TRAILER (a trailer key, whatever its value), AUTHOR_EMAIL (a regular expression matching the whole email of the author) or LOGIN (the exact login or name of the author or committer).")
    val type: AgentMarkerPatternType,
    @APILabel("Value")
    @APIDescription("Regular expression, trailer key or login, depending on the type. Regular expressions and trailer keys ignore the case.")
    val value: String,
)

/**
 * What an [AgentMarkerPattern] is matched against.
 */
enum class AgentMarkerPatternType {
    /**
     * Regular expression matching the whole email of a `Co-Authored-By` trailer.
     */
    CO_AUTHOR_EMAIL,

    /**
     * Key of a trailer, whatever its value.
     */
    TRAILER,

    /**
     * Regular expression matching the whole email of the author.
     */
    AUTHOR_EMAIL,

    /**
     * Exact login (or name) of the author or of the committer.
     */
    LOGIN,
}
