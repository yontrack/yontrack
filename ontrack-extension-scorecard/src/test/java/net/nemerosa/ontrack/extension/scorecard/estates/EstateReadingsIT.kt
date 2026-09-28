package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.extension.scorecard.engine.ReadingEngine
import net.nemerosa.ontrack.extension.scorecard.job.ScorecardJobs
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.settings.ScorecardSettings
import net.nemerosa.ontrack.extension.scorecard.storage.ReadingRepository
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Readings of the estates: their marker, their windows, their targets, and the licence.
 */
class EstateReadingsIT : EstatesTestSupport() {

    @Autowired
    private lateinit var scorecardJobs: ScorecardJobs

    @Autowired
    private lateinit var readingRepository: ReadingRepository

    @Autowired
    private lateinit var readingEngine: ReadingEngine

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

    private fun scorecard(project: Project): JsonNode =
        run(
            """
                query(${'$'}id: Int!) {
                    project(id: ${'$'}id) {
                        scorecard {
                            sets {
                                name
                                readings {
                                    key
                                    value
                                    basis
                                    unknownReason
                                    windowStart
                                    windowEnd
                                    details
                                    direction
                                    target
                                    targetMet
                                }
                            }
                        }
                    }
                }
            """,
            mapOf("id" to project.id())
        ).path("project").path("scorecard")

    @Test
    fun `A promotion-marker estate reads its projects up to its promotion level, over its windows, against its targets`() {
        asAdmin {
            val a = label()
            val project = silverProject().apply { labels = listOf(a) }
            val estate = estate(
                a,
                marker = EstatePromotionMarker("SILVER"),
                readingConfigs = listOf(
                    EstateReadingConfig(ReadingKeys.DELIVERY_FREQUENCY, windowDays = 7, target = 5.0),
                    EstateReadingConfig(ReadingKeys.DELIVERY_LEAD_TIME, target = 3600.0),
                ),
            )

            readingEngine.computeProject(NoEstateReadingSet, project)
            scorecardJobs.computeEstateReadings(estate.id)

            val sets = scorecard(project).path("sets")
            assertEquals(listOf("Project", estate.name), sets.values().map { it.path("name").asText() })
            val noEstate = sets[0].path("readings").values().associateBy { it.path("key").asText() }
            val inEstate = sets[1].path("readings").values().associateBy { it.path("key").asText() }

            // With no estate: read up to GOLD, never granted
            noEstate.getValue(ReadingKeys.DELIVERY_FREQUENCY).let {
                assertEquals(ReadingUnknownReason.NO_SAMPLES.name, it.path("unknownReason").asText())
                assertEquals("GOLD", it.path("details").path("marker").path("levels").path("main").asText())
                assertTrue(it.path("target").isNull)
                assertTrue(it.path("targetMet").isNull)
            }

            // In the estate: read up to SILVER
            inEstate.getValue(ReadingKeys.DELIVERY_FREQUENCY).let {
                assertEquals(ReadingBasis.MEASURED.name, it.path("basis").asText())
                assertEquals("SILVER", it.path("details").path("marker").path("levels").path("main").asText())
                assertEquals("PROMOTION", it.path("details").path("markerKind").asText())
                // One promotion over a week
                assertEquals(1.0, it.path("value").asDouble(), 0.0001)
                // Window override
                assertEquals(7, windowDays(it))
                // Target missed
                assertEquals("HIGHER_IS_BETTER", it.path("direction").asText())
                assertEquals(5.0, it.path("target").asDouble())
                assertFalse(it.path("targetMet").asBoolean())
                assertFalse(it.path("targetMet").isNull)
            }
            inEstate.getValue(ReadingKeys.DELIVERY_LEAD_TIME).let {
                assertEquals(ReadingBasis.MEASURED.name, it.path("basis").asText())
                // Window of the settings
                assertEquals(ScorecardSettings.DEFAULT_WINDOW_DAYS, windowDays(it))
                // Target met
                assertEquals("LOWER_IS_BETTER", it.path("direction").asText())
                assertEquals(3600.0, it.path("target").asDouble())
                assertTrue(it.path("targetMet").asBoolean())
            }
            // No target: shown, not judged
            inEstate.getValue(ReadingKeys.DELIVERY_SUCCESS_RATE).let {
                assertTrue(it.path("target").isNull)
                assertTrue(it.path("targetMet").isNull)
            }
        }
    }

    @Test
    fun `An estate with no marker reads its projects up to the last promotion level of each branch`() {
        asAdmin {
            val a = label()
            val project = silverProject().apply { labels = listOf(a) }
            val estate = estate(a)
            scorecardJobs.computeEstateReadings(estate.id)
            val reading = readingRepository.findLatestByProject(project.id())
                .single { it.estateId == estate.id && it.key == ReadingKeys.DELIVERY_FREQUENCY }
            assertEquals(ReadingUnknownReason.NO_SAMPLES, reading.unknownReason)
            assertEquals("GOLD", reading.details.path("marker").path("levels").path("main").asText())
        }
    }

    @Test
    fun `A promotion level the branches do not have gives no marker`() {
        asAdmin {
            val a = label()
            val project = silverProject().apply { labels = listOf(a) }
            val estate = estate(a, marker = EstatePromotionMarker("PLATINUM"))
            scorecardJobs.computeEstateReadings(estate.id)
            val reading = readingRepository.findLatestByProject(project.id())
                .single { it.estateId == estate.id && it.key == ReadingKeys.DELIVERY_FREQUENCY }
            assertEquals(ReadingUnknownReason.NO_MARKER, reading.unknownReason)
        }
    }

    @Test
    fun `One daily job per estate`() {
        asAdmin {
            val estate = estate(label())
            val keys = scorecardJobs.jobRegistrations.map { it.job.key }
            assertTrue(ScorecardJobs.noEstateJobKey in keys)
            assertTrue(ScorecardJobs.estateJobKey(estate.id) in keys)
            val registration = scorecardJobs.jobRegistrations.single { it.job.key == ScorecardJobs.estateJobKey(estate.id) }
            assertEquals(ScorecardSettings.DEFAULT_CRON, registration.schedule.cron)
        }
    }

    @Test
    fun `Without the licence, the estates raise the licence error, are skipped, and their snapshots come back with it`() {
        asAdmin {
            val a = label()
            val project = silverProject().apply { labels = listOf(a) }
            val computed = estate(a, marker = EstatePromotionMarker("SILVER"))
            val notComputed = estate(a, marker = EstatePromotionMarker("SILVER"))
            scorecardJobs.computeEstateReadings(computed.id)
            val stored = readingRepository.findLatestByProject(project.id()).filter { it.estateId == computed.id }
            assertTrue(stored.isNotEmpty())

            withoutScorecardLicence {
                // The schema is unchanged, and its use raises the licence error
                runWithMatchingError("{ estates { name } }", errorMessage = LICENCE_ERROR)
                runWithMatchingError("""{ estate(name: "${computed.name}") { name } }""", errorMessage = LICENCE_ERROR)
                runWithMatchingError(
                    """
                        mutation {
                            createEstate(input: {name: "Any", labels: ["${a.getDisplay()}"]}) {
                                errors { message }
                            }
                        }
                    """,
                    errorMessage = LICENCE_ERROR
                )
                // No daily job for the estates
                assertTrue(scorecardJobs.jobRegistrations.none { it.job.key == ScorecardJobs.estateJobKey(computed.id) })
                // An estate job which runs anyway skips its estate
                scorecardJobs.computeEstateReadings(notComputed.id)
                assertTrue(readingRepository.findLatestByProject(project.id()).none { it.estateId == notComputed.id })
                // The scorecard shows the no-estate set only
                assertEquals(listOf("Project"), scorecard(project).path("sets").values().map { it.path("name").asText() })
                // The snapshots are kept
                assertEquals(
                    stored.size,
                    readingRepository.findLatestByProject(project.id()).count { it.estateId == computed.id }
                )
            }

            // The snapshots come back with the licence
            val sets = scorecard(project).path("sets")
            assertEquals(
                listOf("Project", computed.name, notComputed.name),
                sets.values().map { it.path("name").asText() }
            )
            assertEquals(
                stored.size,
                sets.values().single { it.path("name").asText() == computed.name }.path("readings").size()
            )
        }
    }

    private fun windowDays(reading: JsonNode): Int {
        val start = java.time.LocalDateTime.parse(reading.path("windowStart").asText().removeSuffix("Z"))
        val end = java.time.LocalDateTime.parse(reading.path("windowEnd").asText().removeSuffix("Z"))
        return ChronoUnit.DAYS.between(start, end).toInt()
    }

    companion object {
        private const val LICENCE_ERROR = "Feature not allowed by the license: extension.scorecard"
    }
}
