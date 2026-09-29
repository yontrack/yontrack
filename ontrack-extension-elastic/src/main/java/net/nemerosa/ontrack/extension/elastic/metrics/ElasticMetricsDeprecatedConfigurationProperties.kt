package net.nemerosa.ontrack.extension.elastic.metrics

import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationPropertiesProvider
import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationProperty
import org.springframework.stereotype.Component

@Component
class ElasticMetricsDeprecatedConfigurationProperties : DeprecatedConfigurationPropertiesProvider {
    override val deprecatedConfigurationProperties = listOf(
        DeprecatedConfigurationProperty(
            name = "${ElasticMetricsConfigProperties.ELASTIC_METRICS_PREFIX}.api-compatibility-mode",
            message = "Removed in V6. No replacement. See #1922",
        ),
    )
}
