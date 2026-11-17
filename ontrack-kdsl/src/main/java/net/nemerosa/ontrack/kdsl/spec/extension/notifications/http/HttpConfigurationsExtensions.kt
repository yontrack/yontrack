package net.nemerosa.ontrack.kdsl.spec.extension.notifications.http

import net.nemerosa.ontrack.kdsl.spec.configurations.ConfigurationInterface
import net.nemerosa.ontrack.kdsl.spec.configurations.ConfigurationsMgt

/**
 * HTTP configurations, used by the `http` channel.
 */
val ConfigurationsMgt.http: ConfigurationInterface<HttpConfiguration>
    get() = ConfigurationInterface(
        connector = connector,
        id = "http",
        type = HttpConfiguration::class,
    )
