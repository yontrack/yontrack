package net.nemerosa.ontrack.extension.indicators

import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationPropertiesProvider
import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationProperty
import org.springframework.stereotype.Component

/**
 * The indicators configuration, which goes with the indicators in V6.
 */
@Component
class IndicatorDeprecatedConfigurationProperties : DeprecatedConfigurationPropertiesProvider {
    override val deprecatedConfigurationProperties = listOf(
        "ontrack.extension.indicators.importing.deleting",
        "ontrack.extension.indicators.metrics.enabled",
        "ontrack.extension.indicators.metrics.cron",
    ).map { name ->
        DeprecatedConfigurationProperty(
            name = name,
            message = "Removed in V6. No replacement. See #1893",
        )
    }
}
