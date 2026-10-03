package net.nemerosa.ontrack.kdsl.spec

import net.nemerosa.ontrack.kdsl.connector.graphql.schema.GlobalMessagesQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

/**
 * A message shown to every user of the instance.
 *
 * @property type Type of the message: `INFO`, `WARNING`, `ERROR`...
 * @property content Text of the message
 */
data class GlobalMessage(
    val type: String,
    val content: String,
)

/**
 * Messages the instance shows to every user, on every page.
 */
val Ontrack.globalMessages: List<GlobalMessage>
    get() = graphqlConnector.query(
        GlobalMessagesQuery()
    )?.globalMessages?.map {
        GlobalMessage(
            type = it.type.rawValue,
            content = it.content,
        )
    } ?: emptyList()
