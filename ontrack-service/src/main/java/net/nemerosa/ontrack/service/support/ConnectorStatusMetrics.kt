package net.nemerosa.ontrack.service.support

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import jakarta.annotation.PostConstruct
import net.nemerosa.ontrack.model.support.CollectedConnectorStatus
import net.nemerosa.ontrack.model.support.ConnectorStatusIndicator
import net.nemerosa.ontrack.model.support.ConnectorStatusType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class ConnectorStatusMetrics(
        private val connectorStatusIndicator: List<ConnectorStatusIndicator>,
        private val connectorStatusJob: ConnectorStatusJob,
        private val registry: MeterRegistry
) {

    private fun MeterRegistry.register(
            name: String,
            gauge: (ConnectorStatusJob) -> List<CollectedConnectorStatus>?,
            tags: List<Tag>,
            extractor: (List<CollectedConnectorStatus>) -> Double
    ) {
        gauge(
                name,
                tags,
                connectorStatusJob
        ) { job ->
            val statuses = gauge(job)
            if (statuses != null) {
                extractor(statuses)
            } else {
                0.0
            }
        }
    }

    @PostConstruct
    fun bindTo() {
        connectorStatusIndicator.forEach { indicator ->

            // Read from the database on each scrape: the statuses are collected by one replica
            val gauge = { job: ConnectorStatusJob -> job.statuses(indicator.type) }

            val tags = listOf(
                    Tag.of("type", indicator.type)
            )

            registry.register("ontrack_connector_count", gauge, tags) { statuses ->
                statuses.size.toDouble()
            }

            registry.register("ontrack_connector_up", gauge, tags) { statuses ->
                statuses.count { it.status.type == ConnectorStatusType.UP }.toDouble()
            }

            registry.register("ontrack_connector_down", gauge, tags) { statuses ->
                statuses.count { it.status.type == ConnectorStatusType.DOWN }.toDouble()
            }

        }
    }

}