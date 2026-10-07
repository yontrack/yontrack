package net.nemerosa.ontrack.kdsl.spec

import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector

/**
 * Header carrying the opaque identifier of an agent session.
 */
const val HTTP_AGENT_SESSION = "X-Yontrack-Agent-Session"

/**
 * Header carrying the link to an agent session.
 */
const val HTTP_AGENT_SESSION_LINK = "X-Yontrack-Agent-Session-Link"

/**
 * A client to the same Yontrack, connected with the given API token instead - for example to act
 * as an agent with one of its tokens.
 *
 * @param token API token to connect with
 * @param agentSession For an agent token, the opaque identifier of the agent session behind the calls
 * @param agentSessionLink For an agent token, the `https` link to the agent session
 * @return A new client
 */
fun Ontrack.withToken(
    token: String,
    agentSession: String? = null,
    agentSessionLink: String? = null,
): Ontrack =
    Ontrack(
        DefaultConnector(
            url = connector.url,
            defaultHeaders = buildMap {
                put(DefaultConnector.X_ONTRACK_TOKEN, token)
                if (agentSession != null) put(HTTP_AGENT_SESSION, agentSession)
                if (agentSessionLink != null) put(HTTP_AGENT_SESSION_LINK, agentSessionLink)
            },
        )
    )
