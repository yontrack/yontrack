package net.nemerosa.ontrack.extension.environments.ui

import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.EnvironmentTestSupport
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleTestFixtures
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Build.journey` - one state per slot of the build's project, which is what the journey chip draws.
 */
@AsAdminTest
class GQLBuildJourneyFieldContributorIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var environmentTestSupport: EnvironmentTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    private fun journey(build: Build, code: (entries: List<JsonNode>) -> Unit) {
        run(
            """
                {
                    build(id: ${build.id}) {
                        journey {
                            slot {
                                id
                            }
                            state
                            pipeline {
                                id
                            }
                            nonEligibleRules {
                                name
                            }
                        }
                    }
                }
            """.trimIndent()
        ) { data ->
            code(data.path("build").path("journey").toList())
        }
    }

    private fun statesBySlot(build: Build): Map<String, String> {
        var result: Map<String, String> = emptyMap()
        journey(build) { entries ->
            result = entries.associate {
                it.path("slot").path("id").asText() to it.path("state").asText()
            }
        }
        return result
    }

    @Test
    fun `A build with no deployment is eligible in every slot which accepts it`() {
        slotTestSupport.withSlot { slot ->
            slot.project.branch {
                build {
                    assertEquals(
                        mapOf(slot.id to "ELIGIBLE"),
                        statesBySlot(this)
                    )
                }
            }
        }
    }

    @Test
    fun `A build refused by an admission rule is not eligible and says which rule refuses it`() {
        slotTestSupport.withSlot { slot ->
            slotService.addAdmissionRuleConfig(
                SlotAdmissionRuleTestFixtures.testBranchPatternAdmissionRuleConfig(slot)
            )
            slot.project.branch(name = "main") {
                build {
                    journey(this) { entries ->
                        val entry = entries.single()
                        assertEquals("NOT_ELIGIBLE", entry.path("state").asText())
                        assertEquals(
                            listOf("releaseBranchesOnly"),
                            entry.path("nonEligibleRules").map { it.path("name").asText() }
                        )
                        assertTrue(entry.path("pipeline").isNull, "No deployment yet")
                    }
                }
            }
        }
    }

    @Test
    fun `A build with a candidate deployment is in progress`() {
        slotTestSupport.withSlot { slot ->
            slot.project.branch {
                build {
                    val pipeline = slotService.startPipeline(slot, this)
                    journey(this) { entries ->
                        val entry = entries.single()
                        assertEquals("IN_PROGRESS", entry.path("state").asText())
                        assertEquals(pipeline.id, entry.path("pipeline").path("id").asText())
                    }
                }
            }
        }
    }

    @Test
    fun `A build with a running deployment is in progress`() {
        slotTestSupport.withSlot { slot ->
            slot.project.branch {
                build {
                    val pipeline = slotService.startPipeline(slot, this)
                    slotService.runDeployment(pipeline.id, dryRun = false)
                    assertEquals(
                        mapOf(slot.id to "IN_PROGRESS"),
                        statesBySlot(this)
                    )
                }
            }
        }
    }

    @Test
    fun `The build a slot holds is deployed`() {
        slotTestSupport.withSlot { slot ->
            slot.project.branch {
                build {
                    slotTestSupport.runAndFinishDeployment(slotService.startPipeline(slot, this))
                    assertEquals(
                        mapOf(slot.id to "DEPLOYED"),
                        statesBySlot(this)
                    )
                }
            }
        }
    }

    @Test
    fun `A build replaced by a newer one is superseded`() {
        slotTestSupport.withSlot { slot ->
            val branch = slot.project.branch()
            val build1 = branch.build()
            val build2 = branch.build()
            slotTestSupport.runAndFinishDeployment(slotService.startPipeline(slot, build1))
            slotTestSupport.runAndFinishDeployment(slotService.startPipeline(slot, build2))

            assertEquals(mapOf(slot.id to "SUPERSEDED"), statesBySlot(build1))
            assertEquals(mapOf(slot.id to "DEPLOYED"), statesBySlot(build2))
        }
    }

    @Test
    fun `The journey covers every slot of the project, in environment order`() {
        project {
            environmentTestSupport.withEnvironment(order = 1) { stagingEnv ->
                environmentTestSupport.withEnvironment(order = 2) { productionEnv ->
                    val staging = slotTestSupport.slot(project = project, environment = stagingEnv)
                    val production = slotTestSupport.slot(project = project, environment = productionEnv)
                    branch {
                        build {
                            slotTestSupport.runAndFinishDeployment(slotService.startPipeline(staging, this))
                            journey(this) { entries ->
                                assertEquals(
                                    listOf(staging.id to "DEPLOYED", production.id to "ELIGIBLE"),
                                    entries.map {
                                        it.path("slot").path("id").asText() to it.path("state").asText()
                                    },
                                    "Staging first, production second"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `The journey covers the qualified slots of the project as well`() {
        project {
            environmentTestSupport.withEnvironment { env ->
                val main = slotTestSupport.slot(project = project, environment = env)
                val canary = slotTestSupport.slot(
                    project = project,
                    environment = env,
                    qualifier = "canary",
                )
                branch {
                    build {
                        assertEquals(
                            mapOf(main.id to "ELIGIBLE", canary.id to "ELIGIBLE"),
                            statesBySlot(this)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `A build of another project is not part of the journey`() {
        slotTestSupport.withSlot { slot ->
            project {
                branch {
                    build {
                        journey(this) { entries ->
                            assertTrue(
                                entries.none { it.path("slot").path("id").asText() == slot.id },
                                "Slots of other projects are not on the journey"
                            )
                        }
                    }
                }
            }
        }
    }

}
