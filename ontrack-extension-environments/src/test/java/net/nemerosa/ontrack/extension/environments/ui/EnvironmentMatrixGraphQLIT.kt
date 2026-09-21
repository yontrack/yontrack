package net.nemerosa.ontrack.extension.environments.ui

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.Environment
import net.nemerosa.ontrack.extension.environments.EnvironmentTestSupport
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.labels.Label
import net.nemerosa.ontrack.model.labels.LabelForm
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.ProjectFavouriteService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The matrix query - the one query the Environments home makes.
 *
 * Every test here narrows the matrix to projects of its own, by giving them a name sharing a unique
 * prefix and filtering on it. The test database is shared, so "the matrix" without a filter is
 * whatever every other test has left behind; with the prefix, the rows, the columns and the totals
 * are the ones this test created and nothing else.
 */
@AsAdminTest
class EnvironmentMatrixGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var environmentTestSupport: EnvironmentTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var projectFavouriteService: ProjectFavouriteService

    /**
     * Runs the matrix query and hands the `environmentMatrix` node to the assertions.
     */
    private fun matrix(
        projectFilter: String,
        tags: List<String>? = null,
        favourites: Boolean = false,
        activity: Boolean = false,
        label: Int? = null,
        offset: Int = 0,
        size: Int = 20,
        code: (matrix: JsonNode) -> Unit,
    ) {
        val tagsArg = tags?.joinToString(", ") { "\"$it\"" }?.let { ", tags: [$it]" } ?: ""
        val labelArg = label?.let { ", label: $it" } ?: ""
        run(
            """
                {
                    environmentMatrix(
                        filter: {
                            project: "$projectFilter",
                            favourites: $favourites,
                            activity: $activity$tagsArg$labelArg
                        },
                        offset: $offset,
                        size: $size
                    ) {
                        totalProjects
                        offset
                        size
                        hasFavourites
                        environments {
                            id
                            name
                            order
                        }
                        projects {
                            project { name }
                            rows {
                                qualifier
                                slots {
                                    id
                                    qualifier
                                    blocked
                                    behind
                                    environment { name }
                                    lastDeployedPipeline { build { name } }
                                    currentPipeline { build { name } status }
                                }
                            }
                        }
                    }
                }
            """.trimIndent()
        ) { data ->
            code(data.path("environmentMatrix"))
        }
    }

    private fun JsonNode.projectNames(): List<String> =
        path("projects").values().map { it.path("project").path("name").asText() }

    private fun JsonNode.environmentNames(): List<String> =
        path("environments").values().map { it.path("name").asText() }

    private fun JsonNode.rows(projectName: String): List<JsonNode> =
        path("projects").first { it.path("project").path("name").asText() == projectName }
            .path("rows")
            .toList()

    /**
     * Two environments in order, and one project having a slot in each.
     */
    private fun withStagingAndProduction(
        prefix: String = uid("mx-"),
        stagingTags: List<String> = listOf("non-production"),
        productionTags: List<String> = listOf("production"),
        code: (prefix: String, project: Project, staging: Slot, production: Slot) -> Unit,
    ) {
        environmentTestSupport.withEnvironment(order = 100, tags = stagingTags) { stagingEnv ->
            environmentTestSupport.withEnvironment(order = 200, tags = productionTags) { productionEnv ->
                val project = project("$prefix-app")
                val staging = slotTestSupport.slot(environment = stagingEnv, project = project)
                val production = slotTestSupport.slot(environment = productionEnv, project = project)
                code(prefix, project, staging, production)
            }
        }
    }

    @Test
    fun `One row per project and one column per environment holding a slot`() {
        withStagingAndProduction { prefix, project, staging, production ->
            matrix(projectFilter = prefix) { matrix ->
                assertEquals(1, matrix.path("totalProjects").asInt(), "One project matches")
                assertEquals(listOf(project.name), matrix.projectNames())
                assertEquals(
                    listOf(staging.environment.name, production.environment.name),
                    matrix.environmentNames(),
                    "Columns by environment order"
                )
                val rows = matrix.rows(project.name)
                assertEquals(1, rows.size, "A project with only the default qualifier has one row")
                assertEquals("", rows[0].path("qualifier").asText())
                assertEquals(
                    listOf(staging.id, production.id),
                    rows[0].path("slots").values().map { it.path("id").asText() },
                    "Slots by environment order"
                )
            }
        }
    }

    @Test
    fun `An environment with no slot among the visible rows is not a column`() {
        withStagingAndProduction { prefix, project, staging, _ ->
            // A third environment, with no slot for this project at all
            environmentTestSupport.withEnvironment(order = 300) { unused ->
                matrix(projectFilter = prefix) { matrix ->
                    assertFalse(
                        unused.name in matrix.environmentNames(),
                        "An environment no visible row can fill is not drawn"
                    )
                    assertTrue(staging.environment.name in matrix.environmentNames())
                }
            }
        }
    }

    @Test
    fun `Several qualifiers nest as one row each, the default one first`() {
        withStagingAndProduction { prefix, project, staging, production ->
            val canary = slotTestSupport.slot(
                environment = production.environment,
                project = project,
                qualifier = "canary",
            )
            matrix(projectFilter = prefix) { matrix ->
                val rows = matrix.rows(project.name)
                assertEquals(
                    listOf("", "canary"),
                    rows.map { it.path("qualifier").asText() },
                    "The default qualifier leads, because that row is the project's own row"
                )
                assertEquals(
                    listOf(staging.id, production.id),
                    rows[0].path("slots").values().map { it.path("id").asText() },
                )
                assertEquals(
                    listOf(canary.id),
                    rows[1].path("slots").values().map { it.path("id").asText() },
                    "The canary row carries only the slots which exist for that qualifier"
                )
            }
        }
    }

    @Test
    fun `The project filter matches a fragment of the name, without case`() {
        withStagingAndProduction { prefix, project, _, _ ->
            matrix(projectFilter = prefix.uppercase()) { matrix ->
                assertEquals(listOf(project.name), matrix.projectNames(), "Case does not matter")
            }
            matrix(projectFilter = "$prefix-no-such-project") { matrix ->
                assertEquals(0, matrix.path("totalProjects").asInt())
                assertEquals(emptyList(), matrix.projectNames())
                assertEquals(emptyList(), matrix.environmentNames(), "No rows, no columns")
            }
        }
    }

    @Test
    fun `The tag filter keeps the environments carrying the tag and drops the others`() {
        withStagingAndProduction { prefix, project, _, production ->
            matrix(projectFilter = prefix, tags = listOf("production")) { matrix ->
                assertEquals(listOf(project.name), matrix.projectNames())
                assertEquals(
                    listOf(production.environment.name),
                    matrix.environmentNames(),
                    "Only the tagged environment is a column"
                )
                assertEquals(
                    listOf(production.id),
                    matrix.rows(project.name)[0].path("slots").values().map { it.path("id").asText() },
                )
            }
        }
    }

    @Test
    fun `A project whose every environment is filtered out by the tags has no row`() {
        withStagingAndProduction(
            stagingTags = listOf("non-production"),
            productionTags = listOf("non-production"),
        ) { prefix, _, _, _ ->
            matrix(projectFilter = prefix, tags = listOf("production")) { matrix ->
                assertEquals(emptyList(), matrix.projectNames(), "A row of blank cells is not a row")
            }
        }
    }

    @Test
    fun `Only with activity keeps the rows with a deployment on the way`() {
        withStagingAndProduction { prefix, project, staging, _ ->
            matrix(projectFilter = prefix, activity = true) { matrix ->
                assertEquals(emptyList(), matrix.projectNames(), "Nothing is moving yet")
            }
            // A candidate on staging: the whole row has activity, production included
            val build = project.branch().build()
            slotService.startPipeline(staging, build)
            matrix(projectFilter = prefix, activity = true) { matrix ->
                assertEquals(listOf(project.name), matrix.projectNames())
                assertEquals(
                    2,
                    matrix.rows(project.name)[0].path("slots").size(),
                    "The row is kept whole, not narrowed to the busy cell"
                )
            }
        }
    }

    @Test
    fun `A finished deployment is not activity`() {
        withStagingAndProduction { prefix, project, staging, _ ->
            slotTestSupport.createRunAndFinishDeployment(slot = staging)
            matrix(projectFilter = prefix, activity = true) { matrix ->
                assertEquals(emptyList(), matrix.projectNames(), "Deployed is not in flight")
            }
        }
    }

    @Test
    fun `The favourites filter keeps the starred projects only`() {
        withStagingAndProduction { prefix, project, _, _ ->
            val other = project("$prefix-other")
            slotTestSupport.slot(project = other)

            matrix(projectFilter = prefix, favourites = true) { matrix ->
                assertEquals(emptyList(), matrix.projectNames(), "Nothing starred yet")
                assertFalse(matrix.path("hasFavourites").asBoolean(), "No favourite at all")
            }

            projectFavouriteService.setProjectFavourite(project, true)
            try {
                matrix(projectFilter = prefix, favourites = true) { matrix ->
                    assertEquals(listOf(project.name), matrix.projectNames())
                    assertTrue(matrix.path("hasFavourites").asBoolean(), "The user has a favourite")
                }
            } finally {
                projectFavouriteService.setProjectFavourite(project, false)
            }
        }
    }

    @Test
    fun `The label filter keeps the projects carrying it`() {
        withStagingAndProduction { prefix, project, _, _ ->
            val other = project("$prefix-other")
            slotTestSupport.slot(project = other)
            val label = createLabel()
            projectLabelManagementService.associateProjectToLabel(project, label)

            matrix(projectFilter = prefix) { matrix ->
                assertEquals(2, matrix.projectNames().size, "Both projects, unfiltered")
            }
            matrix(projectFilter = prefix, label = label.id) { matrix ->
                assertEquals(listOf(project.name), matrix.projectNames())
            }
        }
    }

    @Test
    fun `Projects are paged, by name, with the total saying how many there are`() {
        val prefix = uid("mx-")
        environmentTestSupport.withEnvironment(order = 100) { env ->
            val projects = (1..3).map { index -> project("$prefix-app-$index") }
            projects.forEach { slotTestSupport.slot(environment = env, project = it) }

            matrix(projectFilter = prefix, offset = 0, size = 2) { matrix ->
                assertEquals(3, matrix.path("totalProjects").asInt(), "Three match altogether")
                assertEquals(0, matrix.path("offset").asInt())
                assertEquals(2, matrix.path("size").asInt())
                assertEquals(projects.take(2).map { it.name }, matrix.projectNames())
            }
            matrix(projectFilter = prefix, offset = 2, size = 2) { matrix ->
                assertEquals(3, matrix.path("totalProjects").asInt())
                assertEquals(projects.drop(2).map { it.name }, matrix.projectNames())
            }
        }
    }

    @Test
    fun `A cell carries what is deployed, what is on its way, and the two flags`() {
        withStagingAndProduction { prefix, project, staging, production ->
            val branch = project.branch()
            val deployed = branch.build()
            val onTheWay = branch.build()
            slotTestSupport.runAndFinishDeployment(slotService.startPipeline(staging, deployed))
            slotService.startPipeline(staging, onTheWay)

            matrix(projectFilter = prefix) { matrix ->
                val slots = matrix.rows(project.name)[0].path("slots")
                val stagingCell = slots.first { it.path("id").asText() == staging.id }
                val productionCell = slots.first { it.path("id").asText() == production.id }

                assertEquals(
                    deployed.name,
                    stagingCell.path("lastDeployedPipeline").path("build").path("name").asText(),
                    "The cell shows what the slot is holding"
                )
                assertEquals(
                    onTheWay.name,
                    stagingCell.path("currentPipeline").path("build").path("name").asText(),
                    "...and what is trying to replace it"
                )
                assertFalse(stagingCell.path("behind").asBoolean(), "Staging is the head")
                assertTrue(
                    productionCell.path("behind").asBoolean(),
                    "Production has never been deployed while staging holds a build"
                )
                assertTrue(productionCell.path("lastDeployedPipeline").isNull, "Never deployed")
            }
        }
    }

    private fun createLabel(): Label =
        labelManagementService.newLabel(
            LabelForm(
                category = uid("cat-"),
                name = uid("lbl-"),
                description = null,
                color = "#000000",
            )
        )

}
