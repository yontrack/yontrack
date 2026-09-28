package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.extension.scorecard.engine.MarkerKind
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSets
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EstatesGraphQLIT : EstatesTestSupport() {

    @Autowired
    private lateinit var readingSets: ReadingSets

    @Test
    fun `Projects are selected by all the labels of the estate`() {
        asAdmin {
            val a = label()
            val b = label()
            val c = label()
            val both = project().apply { labels = listOf(a, b) }
            val onlyA = project().apply { labels = listOf(a) }
            val more = project().apply { labels = listOf(c, b, a) }
            val onlyB = project().apply { labels = listOf(b) }
            val estate = estate(a, b)

            // Through the service
            assertEquals(
                listOf(both.name, more.name).sorted(),
                estateService.getProjects(estate).map { it.name }
            )

            // Through GraphQL
            run(
                """
                    query(${'$'}name: String!) {
                        estate(name: ${'$'}name) {
                            name
                            labels { id }
                            projects { name }
                        }
                    }
                """,
                mapOf("name" to estate.name)
            ) { data ->
                val node = data.path("estate")
                assertEquals(estate.name, node.path("name").asText())
                assertEquals(listOf(a.id, b.id).sorted(), node.path("labels").values().map { it.path("id").asInt() }.sorted())
                assertEquals(listOf(both.name, more.name).sorted(), node.path("projects").values().map { it.path("name").asText() })
            }

            // Sets of the projects
            assertEquals(listOf(null, estate.id), readingSets.of(both).map { it.estateId })
            assertEquals(listOf(null, estate.id), readingSets.of(more).map { it.estateId })
            assertEquals(listOf(NoEstateReadingSet), readingSets.of(onlyA))
            assertEquals(listOf(NoEstateReadingSet), readingSets.of(onlyB))
        }
    }

    @Test
    fun `Creating, updating and deleting an estate`() {
        asAdmin {
            val a = label()
            val b = label()
            val name = uid("E")
            val created = assertNoUserError(
                run(
                    """
                        mutation(${'$'}name: String!, ${'$'}labels: [String!]!) {
                            createEstate(input: {
                                name: ${'$'}name,
                                description: "Products",
                                labels: ${'$'}labels,
                                marker: {kind: PROMOTION, levelName: "GOLD"},
                                readings: [
                                    {key: "delivery.leadTime", windowDays: 30, target: 86400},
                                    {key: "delivery.frequency", target: 5},
                                ]
                            }) {
                                estate { $ESTATE_FIELDS }
                                errors { message }
                            }
                        }
                    """,
                    mapOf("name" to name, "labels" to listOf(a.getDisplay()))
                ),
                "createEstate"
            ).path("estate")
            val id = created.path("id").asInt()
            assertEquals(
                mapOf(
                    "id" to id,
                    "name" to name,
                    "description" to "Products",
                    "labels" to listOf(mapOf("id" to a.id)),
                    "marker" to mapOf(
                        "kind" to "PROMOTION",
                        "levelName" to "GOLD",
                        "environment" to null,
                        "qualifier" to null,
                    ),
                    "readingConfigs" to listOf(
                        mapOf(
                            "key" to "delivery.frequency",
                            "windowDays" to null,
                            "target" to 5.0,
                            "direction" to "HIGHER_IS_BETTER",
                        ),
                        mapOf(
                            "key" to "delivery.leadTime",
                            "windowDays" to 30,
                            "target" to 86400.0,
                            "direction" to "LOWER_IS_BETTER",
                        ),
                    ),
                ).asJson(),
                created
            )

            // Update, the whole definition is replaced
            val updated = assertNoUserError(
                run(
                    """
                        mutation(${'$'}id: Int!, ${'$'}name: String!, ${'$'}labels: [String!]!) {
                            updateEstate(input: {
                                id: ${'$'}id,
                                name: ${'$'}name,
                                labels: ${'$'}labels,
                                marker: {kind: ENVIRONMENT, environment: "production", qualifier: "eu"},
                            }) {
                                estate { $ESTATE_FIELDS }
                                errors { message }
                            }
                        }
                    """,
                    mapOf("id" to id, "name" to name, "labels" to listOf(a.getDisplay(), b.getDisplay()))
                ),
                "updateEstate"
            ).path("estate")
            assertEquals(id, updated.path("id").asInt())
            assertTrue(updated.path("description").isNull)
            assertEquals(listOf(a.id, b.id).sorted(), updated.path("labels").values().map { it.path("id").asInt() }.sorted())
            assertEquals(
                mapOf(
                    "kind" to "ENVIRONMENT",
                    "levelName" to null,
                    "environment" to "production",
                    "qualifier" to "eu",
                ).asJson(),
                updated.path("marker")
            )
            assertEquals(0, updated.path("readingConfigs").size())

            // Default marker
            val defaultMarker = estateService.update(
                id,
                EstateInput(name = name, labels = listOf(a.getDisplay()))
            )
            assertEquals(null, defaultMarker.marker)

            // Listed
            run("{ estates { name } }") { data ->
                assertTrue(data.path("estates").values().any { it.path("name").asText() == name })
            }

            // Deletion
            assertNoUserError(
                run(
                    """
                        mutation(${'$'}id: Int!) {
                            deleteEstate(input: {id: ${'$'}id}) {
                                errors { message }
                            }
                        }
                    """,
                    mapOf("id" to id)
                ),
                "deleteEstate"
            )
            run("""{ estate(name: "$name") { id } }""") { data ->
                assertTrue(data.path("estate").isNull)
            }
        }
    }

    @Test
    fun `An estate is checked before being saved`() {
        asAdmin {
            val a = label()
            val existing = estate(a)
            fun create(
                name: String = uid("E"),
                labels: String = "[\"${a.getDisplay()}\"]",
                readings: String = "[]",
                marker: String = "null",
            ) = run(
                """
                    mutation {
                        createEstate(input: {
                            name: "$name",
                            labels: $labels,
                            marker: $marker,
                            readings: $readings,
                        }) {
                            errors { message }
                        }
                    }
                """
            )
            assertUserError(create(name = " "), "createEstate", "The name of an estate is required.")
            assertUserError(
                create(name = existing.name),
                "createEstate",
                "An estate named ${existing.name} already exists."
            )
            assertUserError(
                create(labels = "[]"),
                "createEstate",
                "An estate needs one label at least, to select its projects."
            )
            assertUserError(
                create(labels = "[\"not-a-label-${uid("L")}\"]"),
                "createEstate",
            )
            assertUserError(
                create(readings = """[{key: "delivery.unknown", target: 1}]"""),
                "createEstate",
                "Reading delivery.unknown does not exist."
            )
            assertUserError(
                create(readings = """[{key: "delivery.leadTime", windowDays: 0}]"""),
                "createEstate",
                "The window of reading delivery.leadTime must be one day at least."
            )
            assertUserError(
                create(readings = """[{key: "delivery.leadTime", target: 1}, {key: "delivery.leadTime", target: 2}]"""),
                "createEstate",
                "Reading delivery.leadTime is configured more than once."
            )
            assertUserError(
                create(marker = """{kind: PROMOTION}"""),
                "createEstate",
                "A promotion marker needs the name of a promotion level."
            )
            assertUserError(
                create(marker = """{kind: ENVIRONMENT, qualifier: "eu"}"""),
                "createEstate",
                "An environment marker needs the name of an environment."
            )
        }
    }

    @Test
    fun `A label used by estates cannot be deleted, and the estates are named`() {
        asAdmin {
            val a = label()
            val other = label()
            val prefix = uid("E")
            val first = estate(a, name = "$prefix-1")
            val second = estate(a, other, name = "$prefix-2")

            assertUserError(
                deleteLabel(a.id),
                "deleteLabel",
                "Label ${a.getDisplay()} cannot be deleted: it selects the projects of the estates ${first.name}, ${second.name}."
            )
            assertUserError(
                deleteLabel(other.id),
                "deleteLabel",
                "Label ${other.getDisplay()} cannot be deleted: it selects the projects of the estate ${second.name}."
            )

            // The estates are untouched
            assertEquals(listOf(a.id), estateService.getById(first.id).labels.map { it.id })

            // Once the estates are gone, the label can be deleted
            estateService.delete(first.id)
            estateService.delete(second.id)
            assertNoUserError(deleteLabel(a.id), "deleteLabel")
            assertEquals(null, labelManagementService.findLabelById(a.id))
        }
    }

    @Test
    fun `The estate of a set on the project scorecard`() {
        asAdmin {
            val a = label()
            val project = project().apply { labels = listOf(a) }
            val estate = estate(
                a,
                marker = EstatePromotionMarker("GOLD"),
                readingConfigs = listOf(EstateReadingConfig(ReadingKeys.DELIVERY_LEAD_TIME, target = 3600.0)),
            )
            run(
                """
                    query(${'$'}id: Int!) {
                        project(id: ${'$'}id) {
                            scorecard {
                                sets {
                                    name
                                    estate {
                                        name
                                        marker { kind levelName }
                                    }
                                }
                            }
                        }
                    }
                """,
                mapOf("id" to project.id())
            ) { data ->
                assertEquals(
                    listOf(
                        mapOf("name" to "Project", "estate" to null),
                        mapOf(
                            "name" to estate.name,
                            "estate" to mapOf(
                                "name" to estate.name,
                                "marker" to mapOf("kind" to MarkerKind.PROMOTION.name, "levelName" to "GOLD"),
                            ),
                        ),
                    ).asJson(),
                    data.path("project").path("scorecard").path("sets")
                )
            }
        }
    }

    private fun deleteLabel(id: Int): JsonNode = run(
        """
            mutation(${'$'}id: Int!) {
                deleteLabel(input: {id: ${'$'}id}) {
                    errors { message }
                }
            }
        """,
        mapOf("id" to id)
    )

    companion object {
        private const val ESTATE_FIELDS = """
            id
            name
            description
            labels { id }
            marker { kind levelName environment qualifier }
            readingConfigs { key windowDays target direction }
        """
    }
}
