package net.nemerosa.ontrack.extension.indicators.ui.graphql

import net.nemerosa.ontrack.extension.indicators.AbstractIndicatorsTestSupport
import net.nemerosa.ontrack.extension.indicators.model.Rating
import net.nemerosa.ontrack.extension.indicators.support.Percentage
import net.nemerosa.ontrack.test.assertJsonNotNull
import net.nemerosa.ontrack.test.assertJsonNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GQLTypeIndicatorTypeReportIT : AbstractIndicatorsTestSupport() {

    @Test
    fun `Getting report for a type filtered on rate`() {
        val type = category().percentageType(threshold = 100)
        // Projects with indicator values for this category
        val projects = (1..3).map { project() }
        projects.forEachIndexed { pi, project ->
                project.indicator(type, Percentage(pi * 30))
                /**
                 * List of values
                 *
                 * Project 0
                 *      Type 0    Value = 0    Rating = F
                 * Project 1
                 *      Type 0    Value = 30   Rating = E
                 * Project 2
                 *      Type 0    Value = 60   Rating = C
                 */
        }

        // Getting the view report for different rates
        val expectedRates = mapOf(
            Rating.A to (0..2),
            Rating.B to (0..2),
            Rating.C to (0..2),
            Rating.D to (0..1),
            Rating.E to (0..1),
            Rating.F to (0..0),
        )

        expectedRates.forEach { (rating, expectedProjects) ->

            run("""
                {
                    indicatorTypes {
                        types(id: "${type.id}") {
                            indicators(rate: "$rating") {
                                project {
                                    name
                                }
                            }
                        }
                    }
                }
            """).let { data ->
                val reports = data.path("indicatorTypes").path("types").first().path("indicators")
                val projectNames = reports.values().map { it.path("project").path("name").asText() }
                val expectedProjectNames = expectedProjects.map { index ->
                    projects[index].name
                }

                println("---- Checking for rate $rating")
                println("Expecting: $expectedProjectNames")
                println("Actual   : $projectNames")

                assertEquals(
                    expectedProjectNames,
                    projectNames,
                    "Expecting ${expectedProjectNames.size} projects having rating worse or equal than $rating"
                )
            }
        }

    }

    @Test
    fun `Getting indicator type values for all projects`() {
        val type = category().integerType()
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            project.indicator(type, project.id())
        }
        val unsetProjects = (1..2).map { project() }

        run(
            """
            {
                indicatorTypes {
                    types(id: "${type.id}") {
                        indicators {
                            project {
                                name
                            }
                            value
                        }
                    }
                }
            }
        """
        ).let { data ->
            val indicators = data.path("indicatorTypes").path("types").first().path("indicators")
            val projectIndicators =
                indicators.associateBy { projectIndicator -> projectIndicator.path("project").path("name").asText() }

            // Projects with values
            projects.forEach { project ->
                val projectIndicator = projectIndicators[project.name]
                assertNotNull(projectIndicator) {
                    val value = projectIndicator?.path("value")?.path("value")
                    assertJsonNotNull(value) {
                        assertTrue(this.isInt)
                        assertEquals(project.id(), this.asInt())
                    }
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectIndicator = projectIndicators[project.name]
                assertNotNull(projectIndicator) {
                    val value = projectIndicator?.path("value")?.path("value")
                    assertJsonNull(value)
                }
            }
        }
    }

    @Test
    fun `Getting indicator type values only for projects having an indicator`() {
        val type = category().integerType()
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            project.indicator(type, project.id())
        }
        val unsetProjects = (1..2).map { project() }

        run(
            """
            {
                indicatorTypes {
                    types(id: "${type.id}") {
                        indicators(filledOnly: true) {
                            project {
                                name
                            }
                            value
                        }
                    }
                }
            }
        """
        ).let { data ->
            val indicators = data.path("indicatorTypes").path("types").first().path("indicators")
            val projectIndicators =
                indicators.associateBy { projectIndicator -> projectIndicator.path("project").path("name").asText() }

            // Projects with values
            projects.forEach { project ->
                val projectIndicator = projectIndicators[project.name]
                assertNotNull(projectIndicator) {
                    val value = projectIndicator?.path("value")?.path("value")
                    assertJsonNotNull(value) {
                        assertTrue(this.isInt)
                        assertEquals(project.id(), this.asInt())
                    }
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectIndicator = projectIndicators[project.name]
                assertJsonNull(projectIndicator)
            }
        }
    }

    @Test
    fun `Getting indicator type values for a project identified by name`() {
        val type = category().integerType()
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            project.indicator(type, project.id())
        }
        val unsetProjects = (1..2).map { project() }

        run(
            """
            {
                indicatorTypes {
                    types(id: "${type.id}") {
                        indicators(projectName: "${projects[0].name}") {
                            project {
                                name
                            }
                            value
                        }
                    }
                }
            }
        """
        ).let { data ->
            val indicators = data.path("indicatorTypes").path("types").first().path("indicators")
            val projectIndicators =
                indicators.associateBy { projectIndicator -> projectIndicator.path("project").path("name").asText() }

            // Projects with values
            projects.forEach { project ->
                val projectIndicator = projectIndicators[project.name]
                if (project.name == projects[0].name) {
                    assertNotNull(projectIndicator) {
                        val value = projectIndicator?.path("value")?.path("value")
                        assertJsonNotNull(value) {
                            assertTrue(this.isInt)
                            assertEquals(project.id(), this.asInt())
                        }
                    }
                } else {
                    assertJsonNull(projectIndicator)
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectIndicator = projectIndicators[project.name]
                assertJsonNull(projectIndicator)
            }
        }
    }

    @Test
    fun `Getting indicator type values for a project identified by ID`() {
        val type = category().integerType()
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            project.indicator(type, project.id())
        }
        val unsetProjects = (1..2).map { project() }

        run(
            """
            {
                indicatorTypes {
                    types(id: "${type.id}") {
                        indicators(projectId: ${projects[0].id}) {
                            project {
                                name
                            }
                            value
                        }
                    }
                }
            }
        """
        ).let { data ->
            val indicators = data.path("indicatorTypes").path("types").first().path("indicators")
            val projectIndicators =
                indicators.associateBy { projectIndicator -> projectIndicator.path("project").path("name").asText() }

            // Projects with values
            projects.forEach { project ->
                val projectIndicator = projectIndicators[project.name]
                if (project.name == projects[0].name) {
                    assertNotNull(projectIndicator) {
                        val value = projectIndicator?.path("value")?.path("value")
                        assertJsonNotNull(value) {
                            assertTrue(this.isInt)
                            assertEquals(project.id(), this.asInt())
                        }
                    }
                } else {
                    assertJsonNull(projectIndicator)
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectIndicator = projectIndicators[project.name]
                assertJsonNull(projectIndicator)
            }
        }
    }

    @Test
    fun `Getting indicator type values for projects identified by a portfolio`() {
        val type = category().integerType()
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            project.indicator(type, project.id())
        }
        val unsetProjects = (1..2).map { project() }

        val label = label()
        val portfolio = portfolio(categories = listOf(type.category), label = label)
        projects[0].labels = listOf(label)

        run(
            """
            {
                indicatorTypes {
                    types(id: "${type.id}") {
                        indicators(portfolio: "${portfolio.id}") {
                            project {
                                name
                            }
                            value
                        }
                    }
                }
            }
        """
        ).let { data ->
            val indicators = data.path("indicatorTypes").path("types").first().path("indicators")
            val projectIndicators =
                indicators.associateBy { projectIndicator -> projectIndicator.path("project").path("name").asText() }

            // Projects with values
            projects.forEach { project ->
                val projectIndicator = projectIndicators[project.name]
                if (project.name == projects[0].name) {
                    assertNotNull(projectIndicator) {
                        val value = projectIndicator?.path("value")?.path("value")
                        assertJsonNotNull(value) {
                            assertTrue(this.isInt)
                            assertEquals(project.id(), this.asInt())
                        }
                    }
                } else {
                    assertJsonNull(projectIndicator)
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectIndicator = projectIndicators[project.name]
                assertJsonNull(projectIndicator)
            }
        }
    }

    @Test
    fun `Getting indicator type values for projects identified by a label`() {
        val type = category().integerType()
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            project.indicator(type, project.id())
        }
        val unsetProjects = (1..2).map { project() }

        val label = label()
        projects[0].labels = listOf(label)

        run(
            """
            {
                indicatorTypes {
                    types(id: "${type.id}") {
                        indicators(label: "${label.getDisplay()}") {
                            project {
                                name
                            }
                            value
                        }
                    }
                }
            }
        """
        ).let { data ->
            val indicators = data.path("indicatorTypes").path("types").first().path("indicators")
            val projectIndicators =
                indicators.associateBy { projectIndicator -> projectIndicator.path("project").path("name").asText() }

            // Projects with values
            projects.forEach { project ->
                val projectIndicator = projectIndicators[project.name]
                if (project.name == projects[0].name) {
                    assertNotNull(projectIndicator) {
                        val value = projectIndicator?.path("value")?.path("value")
                        assertJsonNotNull(value) {
                            assertTrue(this.isInt)
                            assertEquals(project.id(), this.asInt())
                        }
                    }
                } else {
                    assertJsonNull(projectIndicator)
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectIndicator = projectIndicators[project.name]
                assertJsonNull(projectIndicator)
            }
        }
    }

}