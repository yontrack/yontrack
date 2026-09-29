package net.nemerosa.ontrack.extension.queue

import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationPropertiesProvider
import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationProperty
import org.springframework.stereotype.Component

/**
 * Deprecated names of the [QueueConfigProperties].
 */
@Component
class QueueDeprecatedConfigurationProperties : DeprecatedConfigurationPropertiesProvider {
    override val deprecatedConfigurationProperties = listOf(
        DeprecatedConfigurationProperty(
            name = "ontrack.extension.queue.general.warn-if-async",
            message = "Removed in V7. Use ontrack.extension.queue.general.warn-if-sync instead. See #1923",
        ),
    )
}
