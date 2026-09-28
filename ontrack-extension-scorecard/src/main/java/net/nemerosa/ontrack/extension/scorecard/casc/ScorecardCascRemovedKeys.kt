package net.nemerosa.ontrack.extension.scorecard.casc

import net.nemerosa.ontrack.extension.casc.removed.CascRemovedKey
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * CasC keys of the delivery metrics, removed when the scorecard took their charts over.
 */
@Configuration
class ScorecardCascRemovedKeys {

    /**
     * Settings of the export of the end-to-end promotion metrics, removed with the delivery metrics.
     */
    @Bean
    fun e2ePromotionMetricsRemovedKey() = CascRemovedKey.settings(
        field = "e2e-promotion-metrics",
        removedIn = "6.0",
    )

}
