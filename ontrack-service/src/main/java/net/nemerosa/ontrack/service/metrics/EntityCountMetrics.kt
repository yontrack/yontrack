package net.nemerosa.ontrack.service.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import net.nemerosa.ontrack.repository.StatsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Gauges of the number of entities, `ontrack_entity_<entity>_total` (project, branch, build,
 * promotionLevel, promotionRun, validationStamp, validationRun, validationRunStatus, property,
 * event), read from the database on each scrape.
 *
 * In the cluster, every replica reports the same values - aggregate them with `max`, not `sum`.
 */
@Component
@Transactional(readOnly = true)
class EntityCountMetrics(
        private val repository: StatsRepository
) : MeterBinder {

    private fun MeterRegistry.count(
            name: String,
            countFn: StatsRepository.() -> Int
    ) {
        gauge(
                "ontrack_entity_${name}_total",
                repository,
                { repository.countFn().toDouble() }
        )
    }

    override fun bindTo(registry: MeterRegistry) {
        registry.count("project", { projectCount })
        registry.count("branch", { branchCount })
        registry.count("build", { buildCount })
        registry.count("promotionLevel", { promotionLevelCount })
        registry.count("promotionRun", { promotionRunCount })
        registry.count("validationStamp", { validationStampCount })
        registry.count("validationRun", { validationRunCount })
        registry.count("validationRunStatus", { validationRunStatusCount })
        registry.count("property", { propertyCount })
        registry.count("event", { eventCount })
    }

}
