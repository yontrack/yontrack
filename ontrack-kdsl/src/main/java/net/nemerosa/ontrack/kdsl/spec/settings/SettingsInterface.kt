package net.nemerosa.ontrack.kdsl.spec.settings

import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.SettingsByIdQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.connector.parseInto
import kotlin.reflect.KClass

class SettingsInterface<T : Any>(
    connector: Connector,
    val id: String,
    val type: KClass<T>,
) : Connected(connector) {

    /**
     * Gets the settings through the REST API, for the administrators only.
     */
    fun get(): T {
        return connector.get("/rest/settings/$id")
            .body.parseInto(type)
    }

    /**
     * Reads the settings through the GraphQL `settingsById` query, which applies the read rule of
     * the settings themselves - unlike [get], whose REST endpoint is for the administrators only.
     */
    fun read(): T =
        graphqlConnector.query(SettingsByIdQuery(id))
            ?.settings?.settingsById?.values?.parseInto(type)
            ?: error("Settings not found: $id")

    fun set(value: T) {
        connector.put("/rest/settings/$id", body = value)
    }

    fun with(update: (T) -> T, code: () -> Unit) {
        val old = get()
        try {
            set(update(old))
            code()
        } finally {
            set(old)
        }
    }

}
