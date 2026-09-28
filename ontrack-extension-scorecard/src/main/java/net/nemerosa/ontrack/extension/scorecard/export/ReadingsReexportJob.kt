package net.nemerosa.ontrack.extension.scorecard.export

import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicense
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.storage.EstateRepository
import net.nemerosa.ontrack.extension.scorecard.storage.ReadingRepository
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.model.metrics.MetricsExportService
import net.nemerosa.ontrack.model.metrics.MetricsReexportJobProvider
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.support.JobProvider
import net.nemerosa.ontrack.model.support.RestorationJobs
import org.springframework.stereotype.Component

/**
 * Re-export of the readings: every stored daily snapshot is exported again as a metric,
 * timestamped with the time it was computed.
 *
 * Launched by the re-export of all the metrics, or by hand. Without the licence of the delivery
 * scorecard, the snapshots of the estates are skipped, like their readings are not shown.
 */
@Component
class ReadingsReexportJob(
    private val securityService: SecurityService,
    private val structureService: StructureService,
    private val readingRepository: ReadingRepository,
    private val estateRepository: EstateRepository,
    private val scorecardLicense: ScorecardLicense,
    private val metricsExportService: MetricsExportService,
) : JobProvider, Job, MetricsReexportJobProvider {

    companion object {
        /**
         * Number of snapshots read and exported at once
         */
        private const val PAGE_SIZE = 1000
    }

    override fun getStartingJobs(): Collection<JobRegistration> = listOf(
        JobRegistration(
            this,
            Schedule.NONE // Manually only
        )
    )

    override fun isDisabled(): Boolean = false

    override fun getReexportJobKey(): JobKey = key

    override fun getKey(): JobKey =
        RestorationJobs.RESTORATION_JOB_TYPE.getKey("scorecard-readings-restoration")

    override fun getDescription(): String = "Re-export of the readings of the delivery scorecard"

    override fun getTask() = JobRun { listener ->
        reexport { listener.message(it) }
    }

    /**
     * Exports every stored snapshot again.
     *
     * @return Number of exported snapshots
     */
    fun reexport(progress: (String) -> Unit = {}): Int = securityService.asAdmin {
        val estates = scorecardLicense.estatesEnabled
        val projectNames = structureService.projectList.associate { it.id() to it.name }
        val estateNames = if (estates) {
            estateRepository.findAll().associate { it.id to it.name }
        } else {
            emptyMap()
        }
        var count = 0
        readingRepository.forEachPage(estates = estates, pageSize = PAGE_SIZE) { page ->
            val metrics = page.mapNotNull { reading ->
                val project = projectNames[reading.projectId]
                val estate = when (val estateId = reading.estateId) {
                    null -> NoEstateReadingSet.tag
                    else -> estateNames[estateId]
                }
                if (project != null && estate != null) {
                    ReadingMetrics.metric(estate, project, reading)
                } else {
                    null
                }
            }
            if (metrics.isNotEmpty()) {
                metricsExportService.batchExportMetrics(metrics)
            }
            count += metrics.size
            progress("$count reading snapshot(s) exported")
        }
        count
    }
}
