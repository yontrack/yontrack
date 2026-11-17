package net.nemerosa.ontrack.kdsl.spec.extension.notifications.http

import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.spec.extension.notifications.NotificationsMgt

/**
 * Management of the HTTP calls of the notifications.
 */
class HttpNotificationsMgt(connector: Connector) : Connected(connector) {

    /**
     * Mock deployment API of the instance, when it is enabled
     * (`ontrack.config.extension.notifications.http.mock.enabled`).
     */
    val mock: HttpMockMgt by lazy { HttpMockMgt(connector) }

}

val NotificationsMgt.http: HttpNotificationsMgt get() = HttpNotificationsMgt(connector)
