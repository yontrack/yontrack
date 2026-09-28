package net.nemerosa.ontrack.extension.scorecard.service

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSets
import net.nemerosa.ontrack.extension.scorecard.job.ScorecardJobs
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.storage.EstateRepository
import net.nemerosa.ontrack.extension.scorecard.storage.ReadingRepository
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.job.Schedule
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.Project
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture

@Service
@Transactional(readOnly = true)
class ScorecardServiceImpl(
    private val securityService: SecurityService,
    private val readingSets: ReadingSets,
    private val readingRepository: ReadingRepository,
    private val scorecardJobs: ScorecardJobs,
    private val jobScheduler: JobScheduler,
    private val estateRepository: EstateRepository,
) : ScorecardService {

    override fun getScorecard(project: Project): Scorecard {
        val readings = readingRepository.findLatestByProject(project.id())
            .groupBy { it.estateId }
        return Scorecard(
            project = project,
            sets = readingSets.of(project).map { set ->
                ScorecardSet(
                    set = set,
                    project = project,
                    readings = (readings[set.estateId] ?: emptyList()).sortedWith(catalogueOrder),
                )
            }
        )
    }

    override fun getHistory(reading: Reading, days: Int): List<Reading> =
        readingRepository.findHistory(
            estateId = reading.estateId,
            projectId = reading.projectId,
            key = reading.key,
            since = Time.now.toLocalDate().minusDays(days.toLong() - 1),
        )

    override fun getTarget(reading: Reading): Double? =
        reading.estateId
            ?.let { estateRepository.findById(it) }
            ?.readingConfig(reading.key)
            ?.target

    override fun recompute(project: Project): CompletableFuture<*>? {
        securityService.checkProjectFunction(project, ProjectConfig::class.java)
        val job = scorecardJobs.recomputeJob(project)
        jobScheduler.schedule(job, Schedule.NONE)
        return jobScheduler.fireImmediately(job.key).orElse(null)
    }

    private val catalogueOrder: Comparator<Reading> =
        compareBy({ ReadingKeys.rank(it.key) }, { it.key })
}
