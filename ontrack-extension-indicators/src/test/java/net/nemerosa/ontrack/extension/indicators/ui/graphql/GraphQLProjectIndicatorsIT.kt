package net.nemerosa.ontrack.extension.indicators.ui.graphql

import net.nemerosa.ontrack.extension.indicators.AbstractIndicatorsTestSupport
import net.nemerosa.ontrack.extension.indicators.support.percent
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.isNullOrNullNode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GraphQLProjectIndicatorsIT : AbstractIndicatorsTestSupport() {

    @Test
    fun `Portfolios associated with a project`() {
        // Labels to use for connecting projects to portfolios
        val label1 = label()
        val label2 = label()
        val label3 = label() // Not linked to any portfolio
        val label4 = label() // Not linked to any project, but assigned to a portfolio
        // Project using those labels
        val project = project {
            labels = listOf(label1, label2, label3)
        }
        // Portfolios for the first two labels
        val portfolio1 = portfolio(label = label1)
        val portfolio2 = portfolio(label = label2)
        portfolio(label = label4)
        // Querying the portfolio for the project
        asUserWithView(project) {
            val data = run("""
                {
                    projects(id: ${project.id}) {
                        indicatorPortfolios {
                            id
                        }
                    }
                }
            """)
            val portfolioIds = data["projects"][0]["indicatorPortfolios"].values().map { it["id"].asText() }
            assertEquals(
                    setOf(portfolio1.id, portfolio2.id),
                    portfolioIds.toSet()
            )
        }
    }

    @Test
    fun `Project category stats for one category`() {
        clearIndicators()

        val category = category()

        val type1 = category.booleanType()
        val type2 = category.booleanType()

        project {
            indicator(type1, true)
            indicator(type2, false)

            val data = run("""{
                projects(id: $id) {
                  projectIndicators {
                    categories {
                      categoryStats {
                        category {
                          id
                        }
                        stats {
                          count
                          avg
                          avgRating
                        }
                      }
                    }
                  }
                }
            }""")

            assertEquals(
                    mapOf(
                            "projects" to listOf(
                                    mapOf(
                                            "projectIndicators" to mapOf(
                                                    "categories" to listOf(
                                                            mapOf(
                                                                    "categoryStats" to mapOf(
                                                                            "category" to mapOf(
                                                                                    "id" to category.id
                                                                            ),
                                                                            "stats" to mapOf(
                                                                                    "count" to 2,
                                                                                    "avg" to 50,
                                                                                    "avgRating" to "D"
                                                                            )
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
                    ).asJson(),
                    data
            )
        }
    }

    @Test
    fun `Project category stats`() {
        clearIndicators()

        val services = category()
        val delivery = category()
        val packaging = category()

        val type1 = services.booleanType()
        val type2 = delivery.booleanType()
        val type3 = packaging.booleanType()
        val type4 = packaging.booleanType()

        project {
            indicator(type1, true)
            indicator(type2, true)
            indicator(type3, false)
            indicator(type4, true)

            val data = run("""{
                projects(id: $id) {
                  projectIndicators {
                    categories {
                      categoryStats {
                        category {
                          id
                        }
                        stats {
                          count
                          avg
                          avgRating
                        }
                      }
                    }
                  }
                }
            }""")

            assertEquals(
                    mapOf(
                            "projects" to listOf(
                                    mapOf(
                                            "projectIndicators" to mapOf(
                                                    "categories" to listOf(
                                                            mapOf(
                                                                    "categoryStats" to mapOf(
                                                                            "category" to mapOf(
                                                                                    "id" to services.id
                                                                            ),
                                                                            "stats" to mapOf(
                                                                                    "count" to 1,
                                                                                    "avg" to 100,
                                                                                    "avgRating" to "A"
                                                                            )
                                                                    )
                                                            ),
                                                            mapOf(
                                                                    "categoryStats" to mapOf(
                                                                            "category" to mapOf(
                                                                                    "id" to delivery.id
                                                                            ),
                                                                            "stats" to mapOf(
                                                                                    "count" to 1,
                                                                                    "avg" to 100,
                                                                                    "avgRating" to "A"
                                                                            )
                                                                    )
                                                            ),
                                                            mapOf(
                                                                    "categoryStats" to mapOf(
                                                                            "category" to mapOf(
                                                                                    "id" to packaging.id
                                                                            ),
                                                                            "stats" to mapOf(
                                                                                    "count" to 2,
                                                                                    "avg" to 50,
                                                                                    "avgRating" to "D"
                                                                            )
                                                                    )
                                                            )
                                                    )
                                            )
                                    )
                            )
                    ).asJson(),
                    data
            )
        }
    }

    @Test
    fun `Indicator history`() {

        val category = category()
        val type = category.percentageType()
        project {
            // Creates an history of indicator, from 0 to 100
            (0..100).forEach { p ->
                indicator(type, p.percent())
            }
            // Gets the first pages of history
            asUserWithView {
                val params = mutableMapOf("project" to id(), "type" to type.id)
                val query = """
                    query IndicatorHistory(${'$'}project: Int!, ${'$'}type: String!, ${'$'}offset: Int!, ${'$'}size: Int!) {
                        projects(id: ${'$'}project) {
                            projectIndicators {
                                indicators(type: ${'$'}type) {
                                    type { id }
                                    value
                                    history(offset: ${'$'}offset, size: ${'$'}size) {
                                        pageInfo {
                                            totalSize
                                            currentOffset
                                            currentSize
                                            previousPage { offset size }
                                            nextPage { offset size }
                                        }
                                        pageItems {
                                            type { id }
                                            value
                                        }
                                    }
                                }
                            }
                        }
                    }
                """
                // First page
                run(query, params + ("offset" to 0) + ("size" to 10)).apply {
                    val indicators = get("projects")[0]["projectIndicators"]["indicators"]
                    assertEquals(1, indicators.size())
                    val indicator = indicators[0]
                    assertEquals(type.id, indicator["type"]["id"].asText())
                    assertEquals(mapOf("value" to 100).asJson(), indicator["value"])
                    // Checks the history
                    val history = indicator["history"]
                    history["pageInfo"].apply {
                        assertEquals(101, get("totalSize").asInt())
                        assertEquals(0, get("currentOffset").asInt())
                        assertEquals(10, get("currentSize").asInt())
                        assertTrue(get("previousPage").isNullOrNullNode())
                        get("nextPage").apply {
                            assertEquals(10, get("offset").asInt())
                            assertEquals(10, get("size").asInt())
                        }
                    }
                    // Checks the items
                    history["pageItems"].forEachIndexed { i, item ->
                        assertEquals(type.id, item["type"]["id"].asText())
                        assertEquals(mapOf("value" to (100 - i)).asJson(), item["value"])
                    }
                }
                // Last page (with more being requested than available)
                run(query, params + ("offset" to 90) + ("size" to 20)).apply {
                    val indicators = get("projects")[0]["projectIndicators"]["indicators"]
                    assertEquals(1, indicators.size())
                    val indicator = indicators[0]
                    assertEquals(type.id, indicator["type"]["id"].asText())
                    assertEquals(mapOf("value" to 100).asJson(), indicator["value"])
                    // Checks the history
                    val history = indicator["history"]
                    history["pageInfo"].apply {
                        assertEquals(101, get("totalSize").asInt())
                        assertEquals(90, get("currentOffset").asInt())
                        assertEquals(11, get("currentSize").asInt())
                        assertTrue(get("nextPage").isNullOrNullNode())
                        get("previousPage").apply {
                            assertEquals(70, get("offset").asInt())
                            assertEquals(20, get("size").asInt())
                        }
                    }
                    // Checks the items
                    history["pageItems"].forEachIndexed { i, item ->
                        assertEquals(type.id, item["type"]["id"].asText())
                        assertEquals(mapOf("value" to (10 - i)).asJson(), item["value"])
                    }
                }
            }
        }
    }

}