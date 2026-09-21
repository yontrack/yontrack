package net.nemerosa.ontrack.kdsl.spec.extension.slack.mock

import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector

class MockSlackMgt(connector: Connector) : Connected(connector) {

    fun getChannelMessages(channel: String): List<MockSlackMessage> {
        return connector.get(
            path = "/extension/slack/mock/channel/${channel.trimStart('#')}"
        ).body.asJsonOrNull()
            ?.values()?.map { node ->
                node.parse<MockSlackMessage>()
            }
            ?: emptyList()
    }

}