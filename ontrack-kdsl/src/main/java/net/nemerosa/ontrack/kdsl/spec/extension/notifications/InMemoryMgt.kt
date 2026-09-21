package net.nemerosa.ontrack.kdsl.spec.extension.notifications

import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector

/**
 * Management interface for the in-memory notification channel.
 */
class InMemoryMgt(connector: Connector) : Connected(connector) {

    /**
     * Gets the list of messages for a given group
     */
    fun group(group: String): List<String> = connector.get("/extension/notifications/in-memory/group/$group")
        .body.asJson().values().map { it.asText() }

}