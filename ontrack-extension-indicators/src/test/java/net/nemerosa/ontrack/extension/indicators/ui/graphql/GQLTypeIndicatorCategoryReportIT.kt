package net.nemerosa.ontrack.extension.indicators.ui.graphql

import net.nemerosa.ontrack.extension.indicators.AbstractIndicatorsTestSupport
import net.nemerosa.ontrack.extension.indicators.model.Rating
import net.nemerosa.ontrack.extension.indicators.support.Percentage
import net.nemerosa.ontrack.test.assertJsonNull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GQLTypeIndicatorCategoryReportIT : AbstractIndicatorsTestSupport() {

    @Test
    fun `Getting report for a category filtered on rate`() {
        // Creating a view with 1 category
        val category = category()
        val types = (1..3).map { no ->
            category.percentageType(id = "${category.id}-$no", threshold = 100) // Percentage == Compliance
        }
        // Projects with indicator values for this category
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
                 * Project 1
                 *      Type 0    Value = 30   Rating = E
                 *      Type 1    Value = 40   Rating = D
                 *      Type 2    Value = 50   Rating = D
                 * Project 2
                 *      Type 0    Value = 60   Rating = C
                 *      Type 1    Value = 70   Rating = C
                 *      Type 2    Value = 80   Rating = B
                 */
            }
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
                    indicatorCategories {
                        categories(id: "${category.id}") {
                            report {
                                projectReport(rate: "$rating") {
                                    project {
                                        name
                                    }
                                    indicators {
                                        rating
                                    }
                                }
                            }
                        }
                    }
                }
            """).let { data ->
                val reports = data.path("indicatorCategories").path("categories").first()
                    .path("report").path("projectReport")
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
    fun `Getting indicator values for all types in a category for all projects`() {
        val category = category()
        val types = (1..3).map { no ->
            category.integerType(id = "${category.id}-$no")
        }
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            types.forEach { type ->
                project.indicator(type, project.id())
            }
        }
        val unsetProjects = (1..2).map { project() }

        run(
            """
            {
                indicatorCategories {
                    categories(id: "${category.id}") {
                        report {
                            projectReport {
                                project {
                                    name
                                }
                                indicators {
                                    type {
                                        id
                                    }
                                    value
                                }
                            }
                            typeReport {
                                type {
                                    id
                                }
                                projectIndicators {
                                    project {
                                        name
                                    }
                                    indicator {
                                        value
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """
        ).let { data ->
            val report = data.path("indicatorCategories").path("categories").first().path("report")

            /**
             * Report indexed by project
             */

            val projectReports = report.path("projectReport").associate { projectReport ->
                projectReport.path("project").path("name").asText() to
                        projectReport.path("indicators")
            }
            // Projects with values
            projects.forEach { project ->
                val projectReport = projectReports[project.name]
                assertNotNull(projectReport) {
                    val indicators = it.associate { indicator ->
                        indicator.path("type").path("id").asText() to
                                indicator.path("value").path("value")
                    }
                    types.forEach { type ->
                        val value = indicators[type.id]
                        assertNotNull(value) { actualValue ->
                            assertTrue(actualValue.isInt)
                            assertEquals(project.id(), actualValue.asInt())
                        }
                    }
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectReport = projectReports[project.name]
                assertNotNull(projectReport) {
                    val indicators = it.associate { indicator ->
                        indicator.path("type").path("id").asText() to
                                indicator.path("value").path("value")
                    }
                    types.forEach { type ->
                        val value = indicators[type.id]
                        assertNotNull(value) { actualValue ->
                            assertTrue(actualValue.isNull || actualValue.isMissingNode)
                        }
                    }
                }
            }

            /**
             * Report indexed by type
             */

            val typeReports = report.path("typeReport").associate { typeReport ->
                typeReport.path("type").path("id").asText() to
                        typeReport.path("projectIndicators")
            }
            // For each type
            types.forEach { type ->
                val typeReport = typeReports[type.id]
                assertNotNull(typeReport) { actualTypeReport ->
                    val projectIndicators = actualTypeReport.associate { projectIndicator ->
                        projectIndicator.path("project").path("name").asText() to
                                projectIndicator.path("indicator").path("value").path("value")
                    }
                    // Projects with values
                    projects.forEach { project ->
                        val projectIndicator = projectIndicators[project.name]
                        assertNotNull(projectIndicator) { actualValue ->
                            assertTrue(actualValue.isInt)
                            assertEquals(project.id(), actualValue.asInt())
                        }
                    }
                    // Projects without value
                    unsetProjects.forEach { project ->
                        val projectIndicator = projectIndicators[project.name]
                        assertNotNull(projectIndicator) { actualValue ->
                            assertTrue(actualValue.isNull || actualValue.isMissingNode)
                        }
                    }
                }
            }

        }
    }

    @Test
    fun `Getting indicator values for all types in a category only for projects having an indicator`() {
        val category = category()
        val types = (1..3).map { no ->
            category.integerType(id = "${category.id}-$no")
        }
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            types.forEach { type ->
                project.indicator(type, project.id())
            }
        }
        val unsetProjects = (1..2).map { project() }

        run(
            """
            {
                indicatorCategories {
                    categories(id: "${category.id}") {
                        report(filledOnly: true) {
                            projectReport {
                                project {
                                    name
                                }
                                indicators {
                                    type {
                                        id
                                    }
                                    value
                                }
                            }
                            typeReport {
                                type {
                                    id
                                }
                                projectIndicators {
                                    project {
                                        name
                                    }
                                    indicator {
                                        value
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """
        ).let { data ->
            val report = data.path("indicatorCategories").path("categories").first().path("report")

            /**
             * Report indexed by project
             */

            val projectReports = report.path("projectReport").associate { projectReport ->
                projectReport.path("project").path("name").asText() to
                        projectReport.path("indicators")
            }
            // Projects with values
            projects.forEach { project ->
                val projectReport = projectReports[project.name]
                assertNotNull(projectReport) {
                    val indicators = it.associate { indicator ->
                        indicator.path("type").path("id").asText() to
                                indicator.path("value").path("value")
                    }
                    types.forEach { type ->
                        val value = indicators[type.id]
                        assertNotNull(value) { actualValue ->
                            assertTrue(actualValue.isInt)
                            assertEquals(project.id(), actualValue.asInt())
                        }
                    }
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectReport = projectReports[project.name]
                assertJsonNull(projectReport)
            }

            /**
             * Report indexed by type
             */

            val typeReports = report.path("typeReport").associate { typeReport ->
                typeReport.path("type").path("id").asText() to
                        typeReport.path("projectIndicators")
            }
            // For each type
            types.forEach { type ->
                val typeReport = typeReports[type.id]
                assertNotNull(typeReport) { actualTypeReport ->
                    val projectIndicators = actualTypeReport.associate { projectIndicator ->
                        projectIndicator.path("project").path("name").asText() to
                                projectIndicator.path("indicator").path("value").path("value")
                    }
                    // Projects with values
                    projects.forEach { project ->
                        val projectIndicator = projectIndicators[project.name]
                        assertNotNull(projectIndicator) { actualValue ->
                            assertTrue(actualValue.isInt)
                            assertEquals(project.id(), actualValue.asInt())
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
    }

    @Test
    fun `Getting indicator values for all types in a category for a project identified by name`() {
        val category = category()
        val types = (1..3).map { no ->
            category.integerType(id = "${category.id}-$no")
        }
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            types.forEach { type ->
                project.indicator(type, project.id())
            }
        }
        val unsetProjects = (1..2).map { project() }

        run(
            """
            {
                indicatorCategories {
                    categories(id: "${category.id}") {
                        report(projectName: "${projects[0].name}") {
                            projectReport {
                                project {
                                    name
                                }
                                indicators {
                                    type {
                                        id
                                    }
                                    value
                                }
                            }
                            typeReport {
                                type {
                                    id
                                }
                                projectIndicators {
                                    project {
                                        name
                                    }
                                    indicator {
                                        value
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """
        ).let { data ->
            val report = data.path("indicatorCategories").path("categories").first().path("report")

            /**
             * Report indexed by project
             */

            val projectReports = report.path("projectReport").associate { projectReport ->
                projectReport.path("project").path("name").asText() to
                        projectReport.path("indicators")
            }
            // Projects with values
            projects.forEach { project ->
                val projectReport = projectReports[project.name]
                if (project.name == projects[0].name) {
                    assertNotNull(projectReport) {
                        val indicators = it.associate { indicator ->
                            indicator.path("type").path("id").asText() to
                                    indicator.path("value").path("value")
                        }
                        types.forEach { type ->
                            val value = indicators[type.id]
                            assertNotNull(value) { actualValue ->
                                assertTrue(actualValue.isInt)
                                assertEquals(project.id(), actualValue.asInt())
                            }
                        }
                    }
                } else {
                    assertJsonNull(projectReport)
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectReport = projectReports[project.name]
                assertJsonNull(projectReport)
            }

            /**
             * Report indexed by type
             */

            val typeReports = report.path("typeReport").associate { typeReport ->
                typeReport.path("type").path("id").asText() to
                        typeReport.path("projectIndicators")
            }
            // For each type
            types.forEach { type ->
                val typeReport = typeReports[type.id]
                assertNotNull(typeReport) { actualTypeReport ->
                    val projectIndicators = actualTypeReport.associate { projectIndicator ->
                        projectIndicator.path("project").path("name").asText() to
                                projectIndicator.path("indicator").path("value").path("value")
                    }
                    // Projects with values
                    projects.forEach { project ->
                        val projectIndicator = projectIndicators[project.name]
                        if (project.name == projects[0].name) {
                            assertNotNull(projectIndicator) { actualValue ->
                                assertTrue(actualValue.isInt)
                                assertEquals(project.id(), actualValue.asInt())
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
    }

    @Test
    fun `Getting indicator values for all types in a category for a project identified by ID`() {
        val category = category()
        val types = (1..3).map { no ->
            category.integerType(id = "${category.id}-$no")
        }
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            types.forEach { type ->
                project.indicator(type, project.id())
            }
        }
        val unsetProjects = (1..2).map { project() }

        run(
            """
            {
                indicatorCategories {
                    categories(id: "${category.id}") {
                        report(projectId: ${projects[0].id}) {
                            projectReport {
                                project {
                                    name
                                }
                                indicators {
                                    type {
                                        id
                                    }
                                    value
                                }
                            }
                            typeReport {
                                type {
                                    id
                                }
                                projectIndicators {
                                    project {
                                        name
                                    }
                                    indicator {
                                        value
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """
        ).let { data ->
            val report = data.path("indicatorCategories").path("categories").first().path("report")

            /**
             * Report indexed by project
             */

            val projectReports = report.path("projectReport").associate { projectReport ->
                projectReport.path("project").path("name").asText() to
                        projectReport.path("indicators")
            }
            // Projects with values
            projects.forEach { project ->
                val projectReport = projectReports[project.name]
                if (project.name == projects[0].name) {
                    assertNotNull(projectReport) {
                        val indicators = it.associate { indicator ->
                            indicator.path("type").path("id").asText() to
                                    indicator.path("value").path("value")
                        }
                        types.forEach { type ->
                            val value = indicators[type.id]
                            assertNotNull(value) { actualValue ->
                                assertTrue(actualValue.isInt)
                                assertEquals(project.id(), actualValue.asInt())
                            }
                        }
                    }
                } else {
                    assertJsonNull(projectReport)
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectReport = projectReports[project.name]
                assertJsonNull(projectReport)
            }

            /**
             * Report indexed by type
             */

            val typeReports = report.path("typeReport").associate { typeReport ->
                typeReport.path("type").path("id").asText() to
                        typeReport.path("projectIndicators")
            }
            // For each type
            types.forEach { type ->
                val typeReport = typeReports[type.id]
                assertNotNull(typeReport) { actualTypeReport ->
                    val projectIndicators = actualTypeReport.associate { projectIndicator ->
                        projectIndicator.path("project").path("name").asText() to
                                projectIndicator.path("indicator").path("value").path("value")
                    }
                    // Projects with values
                    projects.forEach { project ->
                        val projectIndicator = projectIndicators[project.name]
                        if (project.name == projects[0].name) {
                            assertNotNull(projectIndicator) { actualValue ->
                                assertTrue(actualValue.isInt)
                                assertEquals(project.id(), actualValue.asInt())
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
    }

    @Test
    fun `Getting indicator values for all types in a category for projects identified by a portfolio`() {
        val category = category()
        val types = (1..3).map { no ->
            category.integerType(id = "${category.id}-$no")
        }
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            types.forEach { type ->
                project.indicator(type, project.id())
            }
        }
        val unsetProjects = (1..2).map { project() }

        val label = label()
        val portfolio = portfolio(categories = listOf(category), label = label)
        projects[0].labels = listOf(label)

        run(
            """
            {
                indicatorCategories {
                    categories(id: "${category.id}") {
                        report(portfolio: "${portfolio.id}") {
                            projectReport {
                                project {
                                    name
                                }
                                indicators {
                                    type {
                                        id
                                    }
                                    value
                                }
                            }
                            typeReport {
                                type {
                                    id
                                }
                                projectIndicators {
                                    project {
                                        name
                                    }
                                    indicator {
                                        value
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """
        ).let { data ->
            val report = data.path("indicatorCategories").path("categories").first().path("report")

            /**
             * Report indexed by project
             */

            val projectReports = report.path("projectReport").associate { projectReport ->
                projectReport.path("project").path("name").asText() to
                        projectReport.path("indicators")
            }
            // Projects with values
            projects.forEach { project ->
                val projectReport = projectReports[project.name]
                if (project.name == projects[0].name) {
                    assertNotNull(projectReport) {
                        val indicators = it.associate { indicator ->
                            indicator.path("type").path("id").asText() to
                                    indicator.path("value").path("value")
                        }
                        types.forEach { type ->
                            val value = indicators[type.id]
                            assertNotNull(value) { actualValue ->
                                assertTrue(actualValue.isInt)
                                assertEquals(project.id(), actualValue.asInt())
                            }
                        }
                    }
                } else {
                    assertJsonNull(projectReport)
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectReport = projectReports[project.name]
                assertJsonNull(projectReport)
            }

            /**
             * Report indexed by type
             */

            val typeReports = report.path("typeReport").associate { typeReport ->
                typeReport.path("type").path("id").asText() to
                        typeReport.path("projectIndicators")
            }
            // For each type
            types.forEach { type ->
                val typeReport = typeReports[type.id]
                assertNotNull(typeReport) { actualTypeReport ->
                    val projectIndicators = actualTypeReport.associate { projectIndicator ->
                        projectIndicator.path("project").path("name").asText() to
                                projectIndicator.path("indicator").path("value").path("value")
                    }
                    // Projects with values
                    projects.forEach { project ->
                        val projectIndicator = projectIndicators[project.name]
                        if (project.name == projects[0].name) {
                            assertNotNull(projectIndicator) { actualValue ->
                                assertTrue(actualValue.isInt)
                                assertEquals(project.id(), actualValue.asInt())
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
    }

    @Test
    fun `Getting indicator values for all types in a category for projects identified by a label`() {
        val category = category()
        val types = (1..3).map { no ->
            category.integerType(id = "${category.id}-$no")
        }
        val projects = (1..3).map { project() }
        projects.forEach { project ->
            types.forEach { type ->
                project.indicator(type, project.id())
            }
        }
        val unsetProjects = (1..2).map { project() }

        val label = label()
        projects[0].labels = listOf(label)

        run(
            """
            {
                indicatorCategories {
                    categories(id: "${category.id}") {
                        report(label: "${label.getDisplay()}") {
                            projectReport {
                                project {
                                    name
                                }
                                indicators {
                                    type {
                                        id
                                    }
                                    value
                                }
                            }
                            typeReport {
                                type {
                                    id
                                }
                                projectIndicators {
                                    project {
                                        name
                                    }
                                    indicator {
                                        value
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """
        ).let { data ->
            val report = data.path("indicatorCategories").path("categories").first().path("report")

            /**
             * Report indexed by project
             */

            val projectReports = report.path("projectReport").associate { projectReport ->
                projectReport.path("project").path("name").asText() to
                        projectReport.path("indicators")
            }
            // Projects with values
            projects.forEach { project ->
                val projectReport = projectReports[project.name]
                if (project.name == projects[0].name) {
                    assertNotNull(projectReport) {
                        val indicators = it.associate { indicator ->
                            indicator.path("type").path("id").asText() to
                                    indicator.path("value").path("value")
                        }
                        types.forEach { type ->
                            val value = indicators[type.id]
                            assertNotNull(value) { actualValue ->
                                assertTrue(actualValue.isInt)
                                assertEquals(project.id(), actualValue.asInt())
                            }
                        }
                    }
                } else {
                    assertJsonNull(projectReport)
                }
            }
            // Projects without value
            unsetProjects.forEach { project ->
                val projectReport = projectReports[project.name]
                assertJsonNull(projectReport)
            }

            /**
             * Report indexed by type
             */

            val typeReports = report.path("typeReport").associate { typeReport ->
                typeReport.path("type").path("id").asText() to
                        typeReport.path("projectIndicators")
            }
            // For each type
            types.forEach { type ->
                val typeReport = typeReports[type.id]
                assertNotNull(typeReport) { actualTypeReport ->
                    val projectIndicators = actualTypeReport.associate { projectIndicator ->
                        projectIndicator.path("project").path("name").asText() to
                                projectIndicator.path("indicator").path("value").path("value")
                    }
                    // Projects with values
                    projects.forEach { project ->
                        val projectIndicator = projectIndicators[project.name]
                        if (project.name == projects[0].name) {
                            assertNotNull(projectIndicator) { actualValue ->
                                assertTrue(actualValue.isInt)
                                assertEquals(project.id(), actualValue.asInt())
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
    }

}