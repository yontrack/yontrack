package net.nemerosa.ontrack.service.job

import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationPropertiesProvider
import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationProperty
import org.springframework.stereotype.Component

/**
 * Deprecated properties of the jobs.
 *
 * `ontrack.config.jobs.paused-at-startup` pauses all the jobs when a replica starts: since the pause
 * of all the jobs is stored and applies to every replica (#2077), one replica starting with it
 * pauses the whole cluster.
 */
@Component
class JobDeprecatedConfigurationProperties : DeprecatedConfigurationPropertiesProvider {
    override val deprecatedConfigurationProperties = listOf(
        DeprecatedConfigurationProperty(
            name = "ontrack.config.jobs.paused-at-startup",
            message = "Removed in V7. Use the \"Pause all\" action instead. See #2077",
        ),
    )
}
