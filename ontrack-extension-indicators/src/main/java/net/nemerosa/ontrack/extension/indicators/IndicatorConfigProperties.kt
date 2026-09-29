package net.nemerosa.ontrack.extension.indicators

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated

/**
 * Static configuration for the management of indicators
 */
@Component
@ConfigurationProperties(prefix = "ontrack.extension.indicators")
@Validated
@APIName("Indicators configuration")
@APIDescription("Configuration of the indicators")
@Deprecated("Removed in V6. No replacement. See #1893")
class IndicatorConfigProperties {

    /**
     * Import configuration
     */
    var importing = ImportConfig()

    /**
     * Metrics configuration
     */
    var metrics = MetricsConfig()

    /**
     * Import configuration
     */
    class ImportConfig {
        @APIDescription("When a category/type does not exist any longer for a given import ID, must it be deleted?")
        var deleting: Boolean = false
    }

    /**
     * Metrics configuration
     */
    class MetricsConfig {

        @APIDescription("Enabling the scheduled export of metrics (a manual job is always available)")
        var enabled: Boolean = true

        @APIDescription("Cron for the scheduled export of metrics")
        var cron: String = "@daily"
    }

}
