package net.nemerosa.ontrack.extension.scorecard.job

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingEngine
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSets
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.settings.ScorecardSettings
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
 * One job for the no-estate set, over every non-disabled project, on the cron of the
 * [settings][ScorecardSettings]. Being supplied to the job orchestrator, a change of the cron is
 * applied at its next run. The job purges the snapshots past the retention once the readings
 * are computed.
 *
 * The recompute jobs of the projects are created on demand, see [recomputeJob].
 */
@Component
class ScorecardJobs(
    private val securityService: SecurityService,
    private val structureService: StructureService,
    private val cachedSettingsService: CachedSettingsService,
    private val readingEngine: ReadingEngine,
    private val readingSets: ReadingSets,
    private val readingRepository: ReadingRepository,
) : JobOrchestratorSupplier {

    companion object {
        val category: JobCategory = JobCategory.of("scorecard").withName("Delivery scorecard")
        val readingsJobType: JobType = category.getType("readings").withName("Readings computation")
        val recomputeJobType: JobType = category.getType("recompute").withName("Readings recompute")

        /**
         * Key of the daily job of the no-estate set
         */
        val noEstateJobKey: JobKey = readingsJobType.getKey("no-estate")
    }

    override val jobRegistrations: Collection<JobRegistration>
        get() = listOf(
            JobRegistration(
                job = NoEstateReadingsJob(),
                schedule = Schedule.cron(cron()),
            )
        )

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

    private inner class NoEstateReadingsJob : Job {
        override fun getKey(): JobKey = noEstateJobKey
        override fun getTask() = JobRun { listener -> computeNoEstateReadings { listener.message(it) } }
        override fun getDescription(): String = "Readings of every project, with no estate"
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
