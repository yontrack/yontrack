package net.nemerosa.ontrack.extension.scm.changelog.assistants

import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.changelog.assistants.SCMCommitAssistantMarker.*

/**
 * Name of the assistant behind the built-in conventions of Claude Code.
 */
const val ASSISTANT_CLAUDE_CODE = "Claude Code"

/**
 * Name of the assistant behind the built-in conventions of Codex.
 */
const val ASSISTANT_CODEX = "Codex"

/**
 * Name of the assistant behind the built-in conventions of GitHub Copilot.
 */
const val ASSISTANT_COPILOT = "Copilot"

/**
 * Name of the assistant behind the built-in conventions of Devin.
 */
const val ASSISTANT_DEVIN = "Devin"

private const val TRAILER_CO_AUTHORED_BY = "co-authored-by"
private const val TRAILER_ASSISTED_BY = "assisted-by"
private const val TRAILER_CLAUDE_SESSION = "claude-session"

/**
 * Key of a trailer, as git accepts it.
 */
private val trailerKeyRegex = Regex("[A-Za-z0-9][A-Za-z0-9-]*")

/**
 * A trailer line: its key, a colon, its value.
 */
private val trailerLineRegex = Regex("^(${trailerKeyRegex.pattern})\\s*:\\s*(.*)$")

/**
 * Email in a `Name <email>` trailer value.
 */
private val trailerEmailRegex = Regex("<([^<>\\s]+)>")

/**
 * Trailers of a commit message: the `Key: value` lines of its **trailer block**, which is the last
 * paragraph of the message, provided there is more than one paragraph. A trailer anywhere else is
 * prose.
 *
 * Windows line endings are tolerated.
 *
 * @param message Full commit message
 * @return Pairs of key and value, in their order in the message, the value trimmed
 */
fun commitTrailers(message: String): List<Pair<String, String>> {
    val paragraphs = mutableListOf<List<String>>()
    var current = mutableListOf<String>()
    message.lines().forEach { line ->
        val text = line.trimEnd('\r')
        if (text.isBlank()) {
            if (current.isNotEmpty()) {
                paragraphs += current
                current = mutableListOf()
            }
        } else {
            current += text
        }
    }
    if (current.isNotEmpty()) {
        paragraphs += current
    }
    if (paragraphs.size < 2) {
        return emptyList()
    }
    return paragraphs.last().mapNotNull { line ->
        trailerLineRegex.matchEntire(line.trim())?.let { match ->
            match.groupValues[1] to match.groupValues[2].trim()
        }
    }
}

/**
 * Effective rules recognising assistants on commits: the built-in conventions when they are on,
 * plus the patterns of the [settings][AgentMarkersSettings].
 *
 * They are compiled once for given settings. An invalid pattern is skipped: the settings reject it
 * when they are saved, so it can only come from a store edited by hand.
 */
class AgentMarkerRules(settings: AgentMarkersSettings) {

    private val builtInConventions = settings.builtInConventions

    private val coAuthorEmails: List<Pair<Regex, String>>
    private val trailers: Map<String, String>
    private val authorEmails: List<Pair<Regex, String>>
    private val logins: Map<String, String>

    init {
        val coAuthorEmails = mutableListOf<Pair<Regex, String>>()
        val trailers = mutableMapOf<String, String>()
        val authorEmails = mutableListOf<Pair<Regex, String>>()
        val logins = mutableMapOf<String, String>()

        if (builtInConventions) {
            coAuthorEmails += exactEmail("noreply@anthropic.com") to ASSISTANT_CLAUDE_CODE
            coAuthorEmails += exactEmail("codex@openai.com") to ASSISTANT_CODEX
            coAuthorEmails += exactEmail("copilot@github.com") to ASSISTANT_COPILOT
            authorEmails += exactEmail("copilot@github.com") to ASSISTANT_COPILOT
            logins["copilot-swe-agent[bot]"] = ASSISTANT_COPILOT
            logins["devin-ai-integration[bot]"] = ASSISTANT_DEVIN
        }

        settings.patterns
            .filter { checkAgentMarkerPattern(it) == null }
            .forEach { pattern ->
                val name = pattern.name.trim()
                val value = pattern.value.trim()
                when (pattern.type) {
                    AgentMarkerPatternType.CO_AUTHOR_EMAIL -> coAuthorEmails += value.toRegex(RegexOption.IGNORE_CASE) to name
                    AgentMarkerPatternType.TRAILER -> trailers.putIfAbsent(value.lowercase(), name)
                    AgentMarkerPatternType.AUTHOR_EMAIL -> authorEmails += value.toRegex(RegexOption.IGNORE_CASE) to name
                    AgentMarkerPatternType.LOGIN -> logins.putIfAbsent(value.lowercase(), name)
                }
            }

        this.coAuthorEmails = coAuthorEmails
        this.trailers = trailers
        this.authorEmails = authorEmails
        this.logins = logins
    }

    private fun exactEmail(email: String) = Regex(Regex.escape(email), RegexOption.IGNORE_CASE)

    /**
     * Assistants of a commit.
     *
     * @param commit Commit to read
     * @return Assistants of the commit, once each, in the order they were found
     */
    fun assistants(commit: SCMCommit): List<SCMCommitAssistant> {
        val collector = AssistantCollector()

        // Trailers
        commitTrailers(commit.message).forEach { (key, value) ->
            when (val k = key.lowercase()) {
                TRAILER_CO_AUTHORED_BY -> {
                    trailerEmail(value)?.let { email ->
                        coAuthorEmails.firstOrNull { (regex, _) -> regex.matches(email) }?.let { (_, name) ->
                            collector.add(name, CO_AUTHOR)
                        }
                    }
                }

                TRAILER_ASSISTED_BY -> if (builtInConventions) {
                    // Kernel style is `Assisted-by: AGENT_NAME:MODEL_VERSION [TOOL...]`
                    val name = value.substringBefore(':').trim()
                    if (name.isNotBlank()) {
                        collector.add(name, ASSISTED_BY)
                    }
                }

                TRAILER_CLAUDE_SESSION -> if (builtInConventions) {
                    collector.add(ASSISTANT_CLAUDE_CODE, SESSION_TRAILER, sessionLink = value.takeIf { it.isNotBlank() })
                }
            }
            trailers[key.lowercase()]?.let { name ->
                collector.add(name, TRAILER)
            }
        }

        // Author
        commit.authorEmail?.takeIf { it.isNotBlank() }?.trim()?.let { email ->
            authorEmails.firstOrNull { (regex, _) -> regex.matches(email) }?.let { (_, name) ->
                collector.add(name, AUTHOR)
            }
        }
        login(commit.authorLogin, commit.author)?.let { name ->
            collector.add(name, AUTHOR)
        }

        // Committer
        login(commit.committerLogin, commit.committer)?.let { name ->
            collector.add(name, COMMITTER)
        }

        return collector.assistants
    }

    /**
     * Assistant named by a login, or by a name as git records it (bots commit under their login).
     */
    private fun login(vararg candidates: String?): String? =
        candidates.firstNotNullOfOrNull { candidate ->
            candidate?.trim()?.takeIf { it.isNotEmpty() }?.let { logins[it.lowercase()] }
        }

    private fun trailerEmail(value: String): String? =
        trailerEmailRegex.find(value)?.groupValues?.get(1)
            ?: value.trim().takeIf { it.contains('@') && it.none(Char::isWhitespace) }

    private class AssistantCollector {

        private val entries = LinkedHashMap<String, Entry>()

        fun add(name: String, marker: SCMCommitAssistantMarker, sessionLink: String? = null) {
            val entry = entries.getOrPut(name.lowercase()) { Entry(name) }
            entry.markers += marker
            if (entry.sessionLink == null) {
                entry.sessionLink = sessionLink
            }
        }

        val assistants: List<SCMCommitAssistant>
            get() = entries.values.map { entry ->
                SCMCommitAssistant(
                    name = entry.name,
                    markers = entry.markers.toList(),
                    sessionLink = entry.sessionLink,
                )
            }

        private class Entry(val name: String) {
            val markers = LinkedHashSet<SCMCommitAssistantMarker>()
            var sessionLink: String? = null
        }
    }
}

/**
 * Assistants of a commit for some rules.
 *
 * @param commit Commit to read
 * @param rules Effective rules
 * @return Assistants of the commit, once each, in the order they were found
 */
fun commitAssistants(commit: SCMCommit, rules: AgentMarkerRules): List<SCMCommitAssistant> =
    rules.assistants(commit)

/**
 * Checks a list of patterns.
 *
 * @param patterns Patterns to check
 * @return Messages of the problems, empty when the patterns are all valid
 */
fun validateAgentMarkerPatterns(patterns: List<AgentMarkerPattern>): List<String> =
    patterns.mapIndexedNotNull { index, pattern ->
        checkAgentMarkerPattern(pattern)?.let { problem ->
            val name = pattern.name.trim()
            if (name.isEmpty()) {
                "Pattern #${index + 1}: $problem"
            } else {
                "Pattern #${index + 1} ($name): $problem"
            }
        }
    }

/**
 * Checks one pattern.
 *
 * @return The problem, `null` if the pattern is valid
 */
private fun checkAgentMarkerPattern(pattern: AgentMarkerPattern): String? {
    val value = pattern.value.trim()
    return when {
        pattern.name.isBlank() -> "the name is required"
        value.isEmpty() -> "the value is required"
        else -> when (pattern.type) {
            AgentMarkerPatternType.CO_AUTHOR_EMAIL, AgentMarkerPatternType.AUTHOR_EMAIL -> try {
                value.toRegex()
                null
            } catch (_: IllegalArgumentException) {
                "invalid regular expression `$value`"
            }

            AgentMarkerPatternType.TRAILER -> if (trailerKeyRegex.matches(value)) {
                null
            } else {
                "`$value` is not a trailer key"
            }

            AgentMarkerPatternType.LOGIN -> null
        }
    }
}
