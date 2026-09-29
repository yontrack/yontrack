package net.nemerosa.ontrack.service.elasticsearch

import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationPropertiesProvider
import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationProperty
import org.springframework.stereotype.Component

/**
 * Elasticsearch search settings which V6 drops, when Postgres replaces Elasticsearch for search.
 */
@Component
class ElasticSearchDeprecatedConfigurationProperties : DeprecatedConfigurationPropertiesProvider {
    override val deprecatedConfigurationProperties = listOf(
        DeprecatedConfigurationProperty(
            name = "ontrack.config.search.index.immediate",
            message = "Removed in V6. No replacement. See #1882",
        ),
        DeprecatedConfigurationProperty(
            name = "ontrack.config.search.index.ignore-existing",
            message = "Removed in V6. No replacement. See #1882",
        ),
    )
}
