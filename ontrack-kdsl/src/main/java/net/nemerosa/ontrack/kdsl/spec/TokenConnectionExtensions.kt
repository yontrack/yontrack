package net.nemerosa.ontrack.kdsl.spec

import net.nemerosa.ontrack.kdsl.connector.support.DefaultConnector

/**
 * A client to the same Yontrack, connected with the given API token instead - for example to act
 * as an agent with one of its tokens.
 *
 * @param token API token to connect with
 * @return A new client
 */
fun Ontrack.withToken(token: String): Ontrack =
    Ontrack(DefaultConnector(url = connector.url, token = token))
