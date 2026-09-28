package net.nemerosa.ontrack.extension.scorecard.export

import net.nemerosa.ontrack.extension.api.support.TestMetricsExportExtension
import net.nemerosa.ontrack.extension.scorecard.FailingReadingComputer
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingEngine
import net.nemerosa.ontrack.extension.scorecard.estates.EstatePromotionMarker
import net.nemerosa.ontrack.extension.scorecard.estates.EstatesTestSupport
import net.nemerosa.ontrack.extension.scorecard.model.EstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.storage.ReadingRepository
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.metrics.MetricsReexportJobProvider
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Export of the readings as the `ontrack_reading` metric, at each computation and on re-export.
 */
class ReadingsExportIT : EstatesTestSupport() {

    @Autowired
    private lateinit var testMetricsExportExtension: TestMetricsExportExtension

    @Autowired
    private lateinit var readingEngine: ReadingEngine

    @Autowired
    private lateinit var readingRepository: ReadingRepository

    @Autowired
    private lateinit var readingsReexportJob: ReadingsReexportJob

    @Autowired
    private lateinit var metricsReexportJobProviders: List<MetricsReexportJobProvider>

    @Autowired
    private lateinit var jobScheduler: JobScheduler

    @Autowired
    private lateinit var failingReadingComputer: FailingReadingComputer

    /**
     * A project whose branch is promoted to SILVER, never to its last level, GOLD.
     */
    private fun silverProject(): Project = project {
        branch("main") {
            val silver = promotionLevel("SILVER")
            promotionLevel("GOLD")
            build().promote(silver)
        }
    }

    private fun Project.stored(estateId: Int?, key: String): Reading =
        readingRepository.findLatestByProject(id()).single { it.estateId == estateId && it.key == key }

    private fun assertExported(estate: String, project: Project, reading: Reading) {
        testMetricsExportExtension.assertHasMetric(
            metric = "ontrack_reading",
            tags = mapOf(
                "estate" to estate,
                "project" to project.name,
                "reading" to reading.key,
                "basis" to reading.basis.name,
            ),
            fields = reading.value?.let { mapOf("value" to it) } ?: emptyMap(),
            timestamp = reading.computedAt,
        )
    }

    @Test
    fun `The readings of a project with no estate are exported when they are computed`() {
        asAdmin {
            val project = silverProject()
            testMetricsExportExtension.with {
                readingEngine.computeProject(NoEstateReadingSet, project)

                val exported = testMetricsExportExtension.data.filter {
                    it.metric == "ontrack_reading" && it.tags["project"] == project.name
                }
                // One metric per reading
                assertEquals(
                    readingRepository.findLatestByProject(project.id()).map { it.key }.sorted(),
                    exported.map { it.tags.getValue("reading") }.sorted(),
                )
                // Read up to GOLD, never granted: unknown, with no value
                val frequency = project.stored(null, ReadingKeys.DELIVERY_FREQUENCY)
                assertEquals(ReadingBasis.UNKNOWN, frequency.basis)
                assertExported("-", project, frequency)
                val metric = exported.single { it.tags["reading"] == ReadingKeys.DELIVERY_FREQUENCY }
                assertTrue(metric.fields.isEmpty(), "An unknown reading has no value")
            }
        }
    }

    @Test
    fun `The readings of an estate are exported with its name`() {
        asAdmin {
            val a = label()
            val project = silverProject().apply { labels = listOf(a) }
            val estate = estate(a, marker = EstatePromotionMarker("SILVER"))
            testMetricsExportExtension.with {
                readingEngine.computeProject(EstateReadingSet(estate), project)

                val frequency = project.stored(estate.id, ReadingKeys.DELIVERY_FREQUENCY)
                assertEquals(ReadingBasis.MEASURED, frequency.basis)
                assertNotNull(frequency.value)
                assertExported(estate.name, project, frequency)
                // Nothing for the set with no estate
                testMetricsExportExtension.assertNoMetric(
                    metric = "ontrack_reading",
                    tags = mapOf("estate" to "-", "project" to project.name),
                )
            }
        }
    }

    @Test
    fun `A failed computation exports nothing`() {
        asAdmin {
            val project = silverProject()
            failingReadingComputer.failingProjects += project.id()
            try {
                testMetricsExportExtension.with {
                    readingEngine.computeProject(NoEstateReadingSet, project)
                    testMetricsExportExtension.assertNoMetric(
                        metric = "ontrack_reading",
                        tags = mapOf("project" to project.name),
                    )
                }
            } finally {
                failingReadingComputer.failingProjects -= project.id()
            }
        }
    }

    @Test
    fun `The re-export replays every stored snapshot at the time it was computed`() {
        asAdmin {
            val a = label()
            val project = project().apply { labels = listOf(a) }
            val estate = estate(a)
            val old = project.snapshot(estateId = null, day = LocalDate.of(2026, 1, 10), value = 1.0)
            val older = project.snapshot(estateId = null, day = LocalDate.of(2026, 1, 9), value = null)
            val inEstate = project.snapshot(estateId = estate.id, day = LocalDate.of(2026, 1, 10), value = 3.0)
            readingRepository.save(listOf(old, older, inEstate))

            testMetricsExportExtension.with {
                readingsReexportJob.reexport()

                assertExported("-", project, old)
                assertExported("-", project, older)
                assertExported(estate.name, project, inEstate)
                assertEquals(
                    3,
                    testMetricsExportExtension.data.count {
                        it.metric == "ontrack_reading" && it.tags["project"] == project.name
                    }
                )
            }
        }
    }

    @Test
    fun `Without the licence, the re-export skips the snapshots of the estates`() {
        asAdmin {
            val a = label()
            val project = project().apply { labels = listOf(a) }
            val estate = estate(a)
            val noEstate = project.snapshot(estateId = null, day = LocalDate.of(2026, 1, 10), value = 1.0)
            val inEstate = project.snapshot(estateId = estate.id, day = LocalDate.of(2026, 1, 10), value = 3.0)
            readingRepository.save(listOf(noEstate, inEstate))

            withoutScorecardLicence {
                testMetricsExportExtension.with {
                    readingsReexportJob.reexport()

                    assertExported("-", project, noEstate)
                    testMetricsExportExtension.assertNoMetric(
                        metric = "ontrack_reading",
                        tags = mapOf("estate" to estate.name, "project" to project.name),
                    )
                }
            }
        }
    }

    @Test
    fun `The re-export job is launched by the re-export of all the metrics`() {
        val key = readingsReexportJob.getReexportJobKey()
        assertTrue(metricsReexportJobProviders.any { it.getReexportJobKey() == key })
        assertTrue(jobScheduler.getJobStatus(key).isPresent, "The re-export job is registered")
    }

    private fun Project.snapshot(estateId: Int?, day: LocalDate, value: Double?): Reading {
        val computedAt: LocalDateTime = day.atTime(2, 0, 30)
        return Reading(
            estateId = estateId,
            projectId = id(),
            key = ReadingKeys.DELIVERY_LEAD_TIME,
            day = day,
            computedAt = computedAt,
            windowStart = computedAt.minusDays(90),
            windowEnd = computedAt,
            value = value,
            basis = if (value == null) ReadingBasis.UNKNOWN else ReadingBasis.MEASURED,
            unknownReason = null,
            details = emptyMap<String, Any>().asJson(),
        )
    }
}
