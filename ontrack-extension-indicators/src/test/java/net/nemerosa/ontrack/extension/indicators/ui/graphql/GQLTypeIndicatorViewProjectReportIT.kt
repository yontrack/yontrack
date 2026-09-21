package net.nemerosa.ontrack.extension.indicators.ui.graphql

import net.nemerosa.ontrack.extension.indicators.AbstractIndicatorsTestSupport
import net.nemerosa.ontrack.extension.indicators.model.Rating
import net.nemerosa.ontrack.extension.indicators.support.Percentage
import net.nemerosa.ontrack.test.assertJsonNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GQLTypeIndicatorViewProjectReportIT : AbstractIndicatorsTestSupport() {

    @Test
    fun `Getting report for a view`() {
        // Creating a view with 1 category
        val category = category()
        val types = (1..3).map { no ->
            category.booleanType(id = "${category.id}-$no")
        }
        val view = indicatorView(categories = listOf(category))
        // Projects with indicator values for this view's category
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            types.forEachIndexed { index, type ->
                project.indicator(type, index % 2 == 0) // true, false, true ==> 100, 0, 100 ==> 66 avg
            }
        }
        // Projects without indicator values for this view's category
        val unsetProjects = (1..2).map { project() }
        // Getting the view report
        run(
            """
            {
                indicatorViewList {
                    views(id: "${view.id}") {
                        reports {
                            project {
                                name
                            }
                            viewStats {
                                category {
                                    name
                                }
                                stats {
                                    min
                                    avg
                                    max
                                    avgRating
                                }
                            }
                        }
                    }
                }
            }
        """
        ).let { data ->
            val reports = data.path("indicatorViewList").path("views").first().path("reports")

            /**
             * Reports indexed by project
             */

            val projectReports = reports.associate { report ->
                report.path("project").path("name").asText() to report.path("viewStats")
            }

            // Projects with values
            projects.forEach { project ->
                val projectStats = projectReports[project.name]
                assertNotNull(projectStats) { stats ->
                    assertEquals(1, stats.size())
                    val categoryStats = stats.first()
                    assertEquals(category.name, categoryStats.path("category").path("name").asText())
                    assertEquals(0, categoryStats.path("stats").path("min").asInt())
                    assertEquals(66, categoryStats.path("stats").path("avg").asInt())
                    assertEquals(100, categoryStats.path("stats").path("max").asInt())
                    assertEquals("C", categoryStats.path("stats").path("avgRating").asText())
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectStats = projectReports[project.name]
                assertNotNull(projectStats) { stats ->
                    assertEquals(1, stats.size())
                    val categoryStats = stats.first()
                    assertEquals(category.name, categoryStats.path("category").path("name").asText())
                    assertJsonNull(categoryStats.path("stats").path("min"))
                    assertJsonNull(categoryStats.path("stats").path("avg"))
                    assertJsonNull(categoryStats.path("stats").path("max"))
                    assertJsonNull(categoryStats.path("stats").path("avgRating"))
                }
            }
        }
    }

    @Test
    fun `Getting report for a view filtered on rate`() {
        // Creating a view with 1 category
        val category = category()
        val types = (1..3).map { no ->
            category.percentageType(id = "${category.id}-$no", threshold = 100) // Percentage == Compliance
        }
        val view = indicatorView(categories = listOf(category))
        // Projects with indicator values for this view's category
        val projects = (1..3).map { project() }
        projects.forEachIndexed { pi, project ->
            types.forEachIndexed { ti, type ->
                project.indicator(type, Percentage((pi * 3 + ti) * 10))
                /**
                 * List of values
                 *
                 * Project 0
                 *      Type 0    Value = 0    Rating = F
                 *      Type 1    Value = 10   Rating = F
                 *      Type 2    Value = 20   Rating = F
                 *      Avg       Value = 10   Rating = F
                 * Project 1
                 *      Type 0    Value = 30   Rating = E
                 *      Type 1    Value = 40   Rating = D
                 *      Type 2    Value = 50   Rating = D
                 *      Avg       Value = 40   Rating = D
                 * Project 2
                 *      Type 0    Value = 60   Rating = C
                 *      Type 1    Value = 70   Rating = C
                 *      Type 2    Value = 80   Rating = B
                 *      Avg       Value = 70   Rating = C
                 */
            }
        }
        // Getting the view report for different rates

        val expectedRates = mapOf(
            Rating.A to (0..2),
            Rating.B to (0..2),
            Rating.C to (0..2),
            Rating.D to (0..1),
            Rating.E to (0..0),
            Rating.F to (0..0),
        )

        expectedRates.forEach { (rating, expectedProjects) ->

            run(
                """
                {
                    indicatorViewList {
                        views(id: "${view.id}") {
                            reports(rate: "$rating") {
                                project {
                                    name
                                }
                                viewStats {
                                    category {
                                        name
                                    }
                                    stats {
                                        min
                                        avg
                                        max
                                        avgRating
                                    }
                                }
                            }
                        }
                    }
                }
            """
            ).let { data ->
                val reports = data.path("indicatorViewList").path("views").first().path("reports")
                val projectNames = reports.values().map { it.path("project").path("name").asText() }
                val actualRatings =
                    reports.values().map {
                        it.path("viewStats").path(0).path("stats").path("avgRating").asText()
                    }.map {
                        Rating.valueOf(it)
                    }
                val expectedProjectNames = expectedProjects.map { index ->
                    projects[index].name
                }

                println("---- Checking for rate $rating")
                println("Expecting: $expectedProjectNames")
                println("Actual   : $projectNames")
                projectNames.zip(actualRatings).forEach { (p, r) ->
                    println("Project $p, rating = $r")
                }

                assertEquals(
                    expectedProjectNames,
                    projectNames,
                    "Expecting ${expectedProjectNames.size} projects having rating worse or equal than $rating"
                )
            }
        }

    }

}