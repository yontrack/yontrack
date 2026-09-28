package net.nemerosa.ontrack.extension.scorecard.job

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingEngine
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSets
import net.nemerosa.ontrack.extension.scorecard.estates.Estate
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicense
import net.nemerosa.ontrack.extension.scorecard.model.EstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.settings.ScorecardSettings
import net.nemerosa.ontrack.extension.scorecard.storage.EstateRepository
import net.nemerosa.ontrack.extension.scorecard.storage.ReadingRepository
import net.nemerosa.ontrack.job.*
import net.nemerosa.ontrack.job.orchestrator.JobOrchestratorSupplier
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component

/**
 * The daily computation of the readings.
 *
 * One job for the no-estate set, over every non-disabled project, plus one job per estate, over the
 * non-disabled projects it selects, all on the cron of the [settings][ScorecardSettings]. Being
 * supplied to the job orchestrator, a change of the cron, a new or a deleted estate are applied at
 * its next run. The no-estate job purges the snapshots past the retention once the readings are
 * computed.
 *
 * Without the licence of the delivery scorecard, the estate jobs are not supplied, and an estate
 * job which runs anyway skips its estate. The snapshots are kept.
 *
 * The recompute jobs of the projects and of the estates are created on demand, see [recomputeJob]
 * and [estateRecomputeJob].
 */
@Component
class ScorecardJobs(
    private val securityService: SecurityService,
    private val structureService: StructureService,
    private val cachedSettingsService: CachedSettingsService,
    private val readingEngine: ReadingEngine,
    private val readingSets: ReadingSets,
    private val readingRepository: ReadingRepository,
    private val estateRepository: EstateRepository,
    private val scorecardLicense: ScorecardLicense,
) : JobOrchestratorSupplier {

    companion object {
        val category: JobCategory = JobCategory.of("scorecard").withName("Delivery scorecard")
        val readingsJobType: JobType = category.getType("readings").withName("Readings computation")
        val recomputeJobType: JobType = category.getType("recompute").withName("Readings recompute")

        /**
         * Key of the daily job of the no-estate set
         */
        val noEstateJobKey: JobKey = readingsJobType.getKey("no-estate")

        /**
         * Key of the daily job of an estate
         */
        fun estateJobKey(estateId: Int): JobKey = readingsJobType.getKey("estate-$estateId")

        /**
         * Key of the recompute job of an estate
         */
        fun estateRecomputeJobKey(estateId: Int): JobKey = recomputeJobType.getKey("estate-$estateId")
    }

    override val jobRegistrations: Collection<JobRegistration>
        get() {
            val schedule = Schedule.cron(cron())
            val estates = if (scorecardLicense.estatesEnabled) estateRepository.findAll() else emptyList()
            return listOf(
                JobRegistration(
                    job = NoEstateReadingsJob(),
                    schedule = schedule,
                )
            ) + estates.map { estate ->
                JobRegistration(
                    job = EstateReadingsJob(estate.id, estate.name, estateJobKey(estate.id)),
                    schedule = schedule,
                )
            }
        }

    /**
     * Cron of the settings, the default one if it is not valid.
     */
    private fun cron(): String {
        val cron = cachedSettingsService.getCachedSettings(ScorecardSettings::class.java).cron
        return if (CronExpression.isValidExpression(cron)) cron else ScorecardSettings.DEFAULT_CRON
    }

    /**
     * Computation of the readings of every non-disabled project for the no-estate set, then purge
     * of the snapshots past the retention.
     */
    fun computeNoEstateReadings(progress: (String) -> Unit = {}) {
        securityService.asAdmin {
            val projects = structureService.projectList.filter { !it.isDisabled }
            val errors = readingEngine.computeSet(NoEstateReadingSet, projects, progress)
            progress("Readings computed for ${projects.size} project(s), $errors failure(s)")
            purge(progress)
        }
    }

    /**
     * Computation of the readings of an estate, for every non-disabled project it selects. Skipped
     * without the licence, or when the estate is gone.
     */
    fun computeEstateReadings(estateId: Int, progress: (String) -> Unit = {}) {
        if (!scorecardLicense.estatesEnabled) {
            progress("The licence does not allow the estates: estate $estateId skipped")
            return
        }
        securityService.asAdmin {
            val estate = estateRepository.findById(estateId)
            if (estate == null) {
                progress("Estate $estateId not found")
            } else {
                val projects = estateRepository.findProjectIds(estate.id)
                    .mapNotNull { structureService.findProjectByID(ID.of(it)) }
                    .filter { !it.isDisabled }
                val errors = readingEngine.computeSet(EstateReadingSet(estate), projects, progress)
                progress("Readings of ${estate.name} computed for ${projects.size} project(s), $errors failure(s)")
            }
        }
    }

    private fun purge(progress: (String) -> Unit) {
        val retentionDays = cachedSettingsService.getCachedSettings(ScorecardSettings::class.java).retentionDays
        val limit = Time.now.toLocalDate().minusDays(retentionDays.toLong())
        val count = readingRepository.deleteBefore(limit)
        progress("$count snapshot(s) older than $limit purged")
    }

    /**
     * Manual job recomputing the readings of a project, for every set it is in.
     */
    fun recomputeJob(project: Project): Job = RecomputeJob(project.id(), project.name)

    /**
     * Manual job recomputing the readings of an estate, for every non-disabled project it selects.
     */
    fun estateRecomputeJob(estate: Estate): Job =
        EstateReadingsJob(estate.id, estate.name, estateRecomputeJobKey(estate.id))

    private inner class NoEstateReadingsJob : Job {
        override fun getKey(): JobKey = noEstateJobKey
        override fun getTask() = JobRun { listener -> computeNoEstateReadings { listener.message(it) } }
        override fun getDescription(): String = "Readings of every project, with no estate"
        override fun isDisabled(): Boolean = false
    }

    private inner class EstateReadingsJob(
        private val estateId: Int,
        private val estateName: String,
        private val key: JobKey,
    ) : Job {
        override fun getKey(): JobKey = key
        override fun getTask() = JobRun { listener -> computeEstateReadings(estateId) { listener.message(it) } }
        override fun getDescription(): String = "Readings of the estate $estateName"
        override fun isDisabled(): Boolean = false
    }

    private inner class RecomputeJob(
        private val projectId: Int,
        private val projectName: String,
    ) : Job {
        override fun getKey(): JobKey = recomputeJobType.getKey(projectId.toString())

        override fun getTask() = JobRun { listener ->
            securityService.asAdmin {
                val project = structureService.findProjectByID(ID.of(projectId))
                if (project == null) {
                    listener.message("Project $projectName ($projectId) not found")
                } else {
                    readingSets.of(project).forEach { set ->
                        listener.message("Recomputing the readings of ${project.name} for ${set.name}")
                        readingEngine.computeProject(set, project)
                    }
                }
            }
        }

        override fun getDescription(): String = "Recompute of the readings of $projectName"
        override fun isDisabled(): Boolean = false
    }
}
