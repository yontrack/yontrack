package net.nemerosa.ontrack.extension.scorecard.graphql

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingEngine
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.storage.ReadingRepository
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScorecardGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var readingEngine: ReadingEngine

    @Autowired
    private lateinit var readingRepository: ReadingRepository

    private fun scorecard(project: Project, days: Int = 90): JsonNode =
        run(
            """
                query(${'$'}id: Int!, ${'$'}days: Int!) {
                    project(id: ${'$'}id) {
                        scorecard {
                            sets {
                                name
                                readings {
                                    key
                                    day
                                    computedAt
                                    windowStart
                                    windowEnd
                                    value
                                    basis
                                    unknownReason
                                    details
                                    history(days: ${'$'}days) {
                                        day
                                        value
                                    }
                                }
                            }
                        }
                    }
                }
            """,
            mapOf("id" to project.id(), "days" to days)
        ).path("project").path("scorecard")

    @Test
    fun `A project with no computed reading has its no-estate set, empty`() {
        asAdmin {
            project {
                val sets = scorecard(this).path("sets")
                assertEquals(1, sets.size())
                assertEquals("Project", sets[0].path("name").asText())
                assertEquals(0, sets[0].path("readings").size())
            }
        }
    }

    @Test
    fun `Readings of the no-estate set, in the catalogue order, with their history`() {
        asAdmin {
            project {
                branch("main") {
                    val gold = promotionLevel("GOLD")
                    build().promote(gold)
                }
                // Snapshot of yesterday
                val yesterday = Time.now.toLocalDate().minusDays(1)
                readingRepository.save(listOf(snapshot(ReadingKeys.DELIVERY_FREQUENCY, yesterday, 5.0)))
                // Snapshot of today
                readingEngine.computeProject(NoEstateReadingSet, this)

                val readings = scorecard(this).path("sets")[0].path("readings")
                val keys = readings.toList().map { it.path("key").asText() }
                assertEquals(listOf(ReadingKeys.DELIVERY_LEAD_TIME, ReadingKeys.DELIVERY_FREQUENCY), keys.take(2))

                val frequency = readings[1]
                assertEquals(Time.now.toLocalDate().toString(), frequency.path("day").asText())
                assertEquals("MEASURED", frequency.path("basis").asText())
                assertTrue(frequency.path("unknownReason").isNull)
                assertEquals(7.0 / 90.0, frequency.path("value").asDouble(), 1e-9)
                assertEquals(1, frequency.path("details").path("count").asInt())
                assertEquals("PROMOTION", frequency.path("details").path("markerKind").asText())
                assertEquals("GOLD", frequency.path("details").path("marker").path("levels").path("main").asText())
                assertEquals(listOf("main"), frequency.path("details").path("scope").path("branches").toList().map { it.asText() })
                assertTrue(frequency.path("computedAt").asText().isNotBlank())
                assertTrue(frequency.path("windowStart").asText().isNotBlank())
                assertTrue(frequency.path("windowEnd").asText().isNotBlank())
                assertEquals(
                    listOf(yesterday.toString() to 5.0, Time.now.toLocalDate().toString() to 7.0 / 90.0),
                    frequency.path("history").toList().map { it.path("day").asText() to it.path("value").asDouble() }
                )
            }
        }
    }

    @Test
    fun `History limited to the last days`() {
        asAdmin {
            project {
                val today = Time.now.toLocalDate()
                readingRepository.save(
                    listOf(
                        snapshot(ReadingKeys.DELIVERY_FREQUENCY, today.minusDays(10), 1.0),
                        snapshot(ReadingKeys.DELIVERY_FREQUENCY, today.minusDays(2), 2.0),
                        snapshot(ReadingKeys.DELIVERY_FREQUENCY, today, 3.0),
                    )
                )
                val history = scorecard(this, days = 3).path("sets")[0].path("readings")[0].path("history")
                assertEquals(listOf(2.0, 3.0), history.toList().map { it.path("value").asDouble() })
            }
        }
    }

    @Test
    fun `Unknown reading with its reason`() {
        asAdmin {
            project {
                branch("main")
                readingEngine.computeProject(NoEstateReadingSet, this)
                val leadTime = scorecard(this).path("sets")[0].path("readings")[0]
                assertEquals(ReadingKeys.DELIVERY_LEAD_TIME, leadTime.path("key").asText())
                assertEquals("UNKNOWN", leadTime.path("basis").asText())
                assertEquals("NO_MARKER", leadTime.path("unknownReason").asText())
                assertTrue(leadTime.path("value").isNull)
            }
        }
    }

    @Test
    fun `Recompute mutation`() {
        asAdmin {
            project {
                run(
                    """
                        mutation(${'$'}id: Int!) {
                            recomputeProjectScorecard(input: {projectId: ${'$'}id}) {
                                errors {
                                    message
                                }
                            }
                        }
                    """,
                    mapOf("id" to id())
                ) { data ->
                    checkGraphQLUserErrors(data, "recomputeProjectScorecard")
                }
            }
        }
    }

    private fun Project.snapshot(key: String, day: java.time.LocalDate, value: Double) = Reading(
        estateId = null,
        projectId = id(),
        key = key,
        day = day,
        computedAt = day.atTime(2, 0),
        windowStart = day.atTime(2, 0).minusDays(90),
        windowEnd = day.atTime(2, 0),
        value = value,
        basis = ReadingBasis.MEASURED,
        unknownReason = null,
        details = mapOf("count" to 1).asJson(),
    )
}
