package net.nemerosa.ontrack.extension.scorecard.export

import net.nemerosa.ontrack.extension.scorecard.engine.ReadingsListener
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingSet
import net.nemerosa.ontrack.model.metrics.MetricsExportService
import net.nemerosa.ontrack.model.structure.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Exports the readings of a project as metrics each time they are computed and stored.
 *
 * The readings are stored before they are exported: an export which fails is logged, and does not
 * fail the computation. The [re-export][ReadingsReexportJob] replays them.
 */
@Component
class ReadingsExporter(
    private val metricsExportService: MetricsExportService,
) : ReadingsListener {

    private val logger: Logger = LoggerFactory.getLogger(ReadingsExporter::class.java)

    override fun onReadings(set: ReadingSet, project: Project, readings: List<Reading>) {
        try {
            metricsExportService.batchExportMetrics(
                readings.map { ReadingMetrics.metric(set.tag, project.name, it) }
            )
        } catch (any: Exception) {
            logger.error("Cannot export the readings of ${project.name} for ${set.name}", any)
        }
    }
}
