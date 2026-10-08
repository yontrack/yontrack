package net.nemerosa.ontrack.extension.notifications.subscriptions

import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.security.AgentIdentifiers
import net.nemerosa.ontrack.model.structure.defaultDisplayName

/**
 * Prefix of the keyword selecting the kind of actor of an event: `actor:agent` or `actor:human`.
 */
const val KEYWORD_PREFIX_ACTOR = "actor:"

/**
 * Prefix of the keyword selecting one agent: `agent:<slug>[agent]` or `agent:<slug>`.
 */
const val KEYWORD_PREFIX_AGENT = "agent:"

/**
 * `actor:agent` - the actor of the event is an agent.
 */
const val KEYWORD_ACTOR_AGENT = "${KEYWORD_PREFIX_ACTOR}agent"

/**
 * `actor:human` - the actor of the event is not an agent.
 */
const val KEYWORD_ACTOR_HUMAN = "${KEYWORD_PREFIX_ACTOR}human"

/**
 * Does this event match all the space-separated [keywords]? A null or blank list matches every event.
 *
 * - `actor:agent`, `actor:human` and `agent:<slug>[agent]` (or `agent:<slug>`) are read from the
 *   actor of the event (`signature.actor`), never from its entities or values
 * - every other keyword must be, ignoring the case, the name of one of the entities of the event,
 *   or one of its values
 *
 * Keywords are case-insensitive and AND-ed.
 */
fun Event.matchesKeywords(keywords: String?) =
    if (!keywords.isNullOrBlank()) {
        val tokens = keywords.split(" ").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        tokens.all { matchesKeyword(it) }
    } else {
        true
    }

private fun Event.matchesKeyword(keyword: String) =
    when {
        keyword.startsWith(KEYWORD_PREFIX_ACTOR) -> matchesActorKeyword(keyword)
        keyword.startsWith(KEYWORD_PREFIX_AGENT) -> matchesAgentKeyword(keyword.removePrefix(KEYWORD_PREFIX_AGENT))
        else -> matchesPlainKeyword(keyword)
    }

private fun Event.matchesActorKeyword(keyword: String) =
    when (keyword) {
        KEYWORD_ACTOR_AGENT -> signature?.actor != null
        KEYWORD_ACTOR_HUMAN -> signature?.actor == null
        else -> false
    }

/**
 * @param agent Lowercase identifier (`<slug>[agent]`) or slug of the agent
 */
private fun Event.matchesAgentKeyword(agent: String): Boolean {
    if (agent.isBlank()) return false
    val identifier = if (AgentIdentifiers.isAgentIdentifier(agent)) agent else AgentIdentifiers.identifier(agent)
    return signature?.actor?.agent?.lowercase() == identifier
}

private fun Event.matchesPlainKeyword(keyword: String) =
    entities.values.any { entity -> entity.defaultDisplayName.lowercase() == keyword }
            || extraEntities.values.any { entity -> entity.defaultDisplayName.lowercase() == keyword }
            || values.values.any { it.value.lowercase() == keyword }
