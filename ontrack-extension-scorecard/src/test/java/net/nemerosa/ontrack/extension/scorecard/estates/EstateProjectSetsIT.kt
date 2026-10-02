package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.extension.scorecard.engine.ReadingEngine
import net.nemerosa.ontrack.extension.scorecard.job.ScorecardJobs
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Estate.projectSets`: the set of the estate of each of its projects, for the estate view.
 */
class EstateProjectSetsIT : EstatesTestSupport() {

    @Autowired
    private lateinit var scorecardJobs: ScorecardJobs

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

    private fun projectSets(name: String): JsonNode =
        run(
            """
                query(${'$'}name: String!) {
                    estate(name: ${'$'}name) {
                        projectSets {
                            name
                            estate { name }
                            project { id name }
                            readings {
                                key
                                value
                                basis
                                unknownReason
                                target
                                targetMet
                            }
                        }
                    }
                }
            """,
            mapOf("name" to name)
        ).path("estate").path("projectSets")

    @Test
    fun `The set of the estate of each of its projects, by project name, with the readings of the estate only`() {
        asAdmin {
            val a = label()
            val first = silverProject().apply { labels = listOf(a) }
            val second = silverProject().apply { labels = listOf(a) }
            // Not in the estate
            silverProject()
            val estate = estate(
                a,
                marker = EstatePromotionMarker("SILVER"),
                readingConfigs = listOf(EstateReadingConfig(ReadingKeys.DELIVERY_FREQUENCY, windowDays = 7, target = 5.0)),
            )
            // Both sets are computed, only the estate's one is read
            readingEngine.computeProject(NoEstateReadingSet, first)
            scorecardJobs.computeEstateReadings(estate.id)

            val sets = projectSets(estate.name)
            assertEquals(
                listOf(first.name, second.name).sorted(),
                sets.values().map { it.path("project").path("name").asText() }
            )
            sets.values().forEach { set ->
                assertEquals(estate.name, set.path("name").asText())
                assertEquals(estate.name, set.path("estate").path("name").asText())
                val readings = set.path("readings").values().toList()
                // Every reading of the catalogue, in its order, the ones out of it (of the tests) last
                assertEquals(ReadingKeys.ORDER, readings.map { it.path("key").asText() }.take(ReadingKeys.ORDER.size))
                // Read up to SILVER, against the target of the estate
                val frequency = readings.single { it.path("key").asText() == ReadingKeys.DELIVERY_FREQUENCY }
                assertEquals("MEASURED", frequency.path("basis").asText())
                assertEquals(1.0, frequency.path("value").asDouble(), 0.0001)
                assertEquals(5.0, frequency.path("target").asDouble())
                assertEquals(false, frequency.path("targetMet").asBoolean())
            }
        }
    }

    @Test
    fun `A project of the estate with no computed readings has its set, empty`() {
        asAdmin {
            val a = label()
            val project = silverProject().apply { labels = listOf(a) }
            val estate = estate(a)
            // The no-estate set only
            readingEngine.computeProject(NoEstateReadingSet, project)

            val sets = projectSets(estate.name)
            assertEquals(1, sets.size())
            assertEquals(project.id(), sets[0].path("project").path("id").asInt())
            assertEquals(0, sets[0].path("readings").size())
        }
    }

    @Test
    fun `The sets of an estate are filtered by the right to see their projects`() {
        val a = asAdmin { label() }
        val visible = asAdmin { silverProject().apply { labels = listOf(a) } }
        asAdmin { silverProject().apply { labels = listOf(a) } }
        val estate = estate(a)
        asAdmin { scorecardJobs.computeEstateReadings(estate.id) }
        withNoGrantViewToAll {
            asUserWithView(visible).call {
                val sets = projectSets(estate.name)
                assertEquals(listOf(visible.name), sets.values().map { it.path("project").path("name").asText() })
                assertTrue(sets[0].path("readings").size() > 0)
            }
        }
    }
}
