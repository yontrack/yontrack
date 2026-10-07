package net.nemerosa.ontrack.model.security

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

/**
 * Raw values of the HTTP headers by which an agent identifies its [session][ActorAgentSession].
 *
 * They are read only for an agent token: the headers of any other caller are ignored. A value which
 * does not fit is dropped with a log warning, never refused.
 *
 * @property id Value of [HTTP_AGENT_SESSION]
 * @property link Value of [HTTP_AGENT_SESSION_LINK]
 */
data class AgentSessionHeaders(
    val id: String?,
    val link: String?,
) {

    /**
     * The agent session these headers give, if any.
     *
     * - the identifier is trimmed; a blank one, or one longer than [MAX_ID_LENGTH] characters,
     *   gives no session at all;
     * - the link is trimmed; it is dropped when it is not an absolute `https` URL of at most
     *   [MAX_LINK_LENGTH] characters, and the session is kept without it.
     */
    fun parse(): ActorAgentSession? {
        val sessionId = id?.trim()?.takeIf { it.isNotEmpty() }
        val sessionLink = link?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            sessionId == null -> {
                if (sessionLink != null) {
                    logger.warn("Agent session link ignored because no agent session is given ($HTTP_AGENT_SESSION).")
                }
                null
            }

            sessionId.length > MAX_ID_LENGTH -> {
                logger.warn("Agent session ignored because its identifier is longer than $MAX_ID_LENGTH characters.")
                null
            }

            else -> ActorAgentSession(
                id = sessionId,
                link = sessionLink?.let { checkLink(it) },
            )
        }
    }

    private fun checkLink(link: String): String? = when {
        link.length > MAX_LINK_LENGTH -> {
            logger.warn("Agent session link dropped because it is longer than $MAX_LINK_LENGTH characters.")
            null
        }

        !isAbsoluteHttps(link) -> {
            logger.warn("Agent session link dropped because it is not an absolute https URL: {}", link)
            null
        }

        else -> link
    }

    private fun isAbsoluteHttps(link: String): Boolean =
        try {
            val uri = URI(link)
            uri.isAbsolute && uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }

    companion object {

        private val logger: Logger = LoggerFactory.getLogger(AgentSessionHeaders::class.java)

        /**
         * Header carrying the opaque identifier of the agent session.
         */
        const val HTTP_AGENT_SESSION = "X-Yontrack-Agent-Session"

        /**
         * Header carrying the link to the agent session.
         */
        const val HTTP_AGENT_SESSION_LINK = "X-Yontrack-Agent-Session-Link"

        /**
         * Maximum length of the identifier of an agent session.
         */
        const val MAX_ID_LENGTH = 255

        /**
         * Maximum length of the link to an agent session.
         */
        const val MAX_LINK_LENGTH = 1000
    }
}
