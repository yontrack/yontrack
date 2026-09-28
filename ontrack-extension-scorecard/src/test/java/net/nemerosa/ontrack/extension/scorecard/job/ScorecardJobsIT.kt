package net.nemerosa.ontrack.extension.scorecard.job

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.scorecard.FailingReadingComputer
import net.nemerosa.ontrack.extension.scorecard.metrics.ScorecardMetrics
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.settings.ScorecardSettings
import net.nemerosa.ontrack.extension.scorecard.storage.ReadingRepository
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The daily job runs in its own transactions: the data of the tests must be committed,
 * and the projects are deleted at the end.
 */
class ScorecardJobsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var scorecardJobs: ScorecardJobs

    @Autowired
    private lateinit var readingRepository: ReadingRepository

    @Autowired
    private lateinit var failingReadingComputer: FailingReadingComputer

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    private val today: LocalDate get() = Time.now.toLocalDate()

    @Test
    fun `The daily job is scheduled on the cron of the settings`() {
        withSettings<ScorecardSettings> {
            asAdmin {
                settingsManagerService.saveSettings(ScorecardSettings(cron = "0 15 4 * * *"))
            }
            val registration = scorecardJobs.jobRegistrations.single { it.job.key == ScorecardJobs.noEstateJobKey }
            assertEquals("0 15 4 * * *", registration.schedule.cron)
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun `The daily job computes every non-disabled project, isolates the failures and purges the old snapshots`() {
        val projects = mutableListOf<Project>()
        try {
            asAdmin {
                val ok = project().apply { branch("main") { promotionLevel("GOLD") } }.also { projects += it }
                val failing = project().apply { branch("main") }.also { projects += it }
                val disabled = project().apply { branch("main") }.also { projects += it }
                structureService.disableProject(disabled)
                failingReadingComputer.failingProjects += failing.id()

                // Old snapshots
                readingRepository.save(listOf(ok.reading(today.minusDays(731)), ok.reading(today.minusDays(730))))

                val errors = meterRegistry.counter(ScorecardMetrics.errors, ScorecardMetrics.Tags.ESTATE, "-").count()

                scorecardJobs.computeNoEstateReadings()

                // Readings of the project
                val readings = readingRepository.findLatestByProject(ok.id()).filter { it.day == today }
                assertEquals(
                    listOf(
                        ReadingKeys.DELIVERY_FREQUENCY,
                        ReadingKeys.DELIVERY_LEAD_TIME,
                        ReadingKeys.DELIVERY_MTTR,
                        ReadingKeys.DELIVERY_SUCCESS_RATE,
                        FailingReadingComputer.KEY,
                    ),
                    readings.map { it.key }.sorted()
                )
                assertTrue(readings.all { it.estateId == null })
                // No row for the failing project, counted
                assertEquals(emptyList(), readingRepository.findLatestByProject(failing.id()))
                assertTrue(
                    meterRegistry.counter(ScorecardMetrics.errors, ScorecardMetrics.Tags.ESTATE, "-").count() >= errors + 1
                )
                // Nothing for the disabled project
                assertEquals(emptyList(), readingRepository.findLatestByProject(disabled.id()))
                // Retention (730 days by default)
                assertEquals(
                    listOf(today.minusDays(730)),
                    readingRepository.findHistory(null, ok.id(), "test.old", today.minusDays(1000)).map { it.day }
                )
            }
        } finally {
            failingReadingComputer.failingProjects.clear()
            asAdmin {
                projects.forEach { structureService.deleteProject(it.id) }
            }
        }
    }

    private fun Project.reading(day: LocalDate) = Reading(
        estateId = null,
        projectId = id(),
        key = "test.old",
        day = day,
        computedAt = day.atTime(2, 0),
        windowStart = day.atTime(2, 0).minusDays(90),
        windowEnd = day.atTime(2, 0),
        value = 1.0,
        basis = ReadingBasis.MEASURED,
        unknownReason = null,
        details = emptyMap<String, Any>().asJson(),
    )
}
