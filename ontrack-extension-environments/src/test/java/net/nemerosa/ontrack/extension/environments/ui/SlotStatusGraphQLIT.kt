package net.nemerosa.ontrack.extension.environments.ui

import net.nemerosa.ontrack.extension.environments.EnvironmentTestSupport
import net.nemerosa.ontrack.extension.environments.SlotAdmissionRuleTestFixtures
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The three operational readings a slot cell needs: `blocked`, `behind` and `nextBuilds`.
 */
@AsAdminTest
class SlotStatusGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var environmentTestSupport: EnvironmentTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    private fun slotFlags(slotId: String, block: (blocked: Boolean, behind: Boolean) -> Unit) {
        run(
            """
                {
                    slotById(id: "$slotId") {
                        blocked
                        behind
                    }
                }
            """.trimIndent()
        ) { data ->
            val slot = data.path("slotById")
            block(slot.path("blocked").asBoolean(), slot.path("behind").asBoolean())
        }
    }

    @Test
    fun `A slot with nothing in flight is not blocked`() {
        slotTestSupport.withSlot { slot ->
            slotFlags(slot.id) { blocked, _ ->
                assertFalse(blocked, "Idle slot is not blocked")
            }
        }
    }

    @Test
    fun `A candidate whose rules all pass is not blocked`() {
        slotTestSupport.withSlotPipeline { pipeline ->
            slotFlags(pipeline.slot.id) { blocked, _ ->
                assertFalse(blocked, "Candidate with no failing rule is not blocked")
            }
        }
    }

    @Test
    fun `A candidate waiting for a manual approval is blocked`() {
        slotTestSupport.withSlot { slot ->
            slotService.addAdmissionRuleConfig(
                SlotAdmissionRuleTestFixtures.testManualApprovalRuleConfig(slot)
            )
            slot.project.branch {
                build {
                    slotService.startPipeline(slot, this)
                    slotFlags(slot.id) { blocked, _ ->
                        assertTrue(blocked, "Candidate waiting for a manual approval is blocked")
                    }
                }
            }
        }
    }

    @Test
    fun `A blocked candidate is no longer blocked once its rule is overridden`() {
        slotTestSupport.withSlot { slot ->
            val config = SlotAdmissionRuleTestFixtures.testManualApprovalRuleConfig(slot)
            slotService.addAdmissionRuleConfig(config)
            slot.project.branch {
                build {
                    val pipeline = slotService.startPipeline(slot, this)
                    slotService.overrideAdmissionRule(pipeline, config, "Approved by hand")
                    slotFlags(slot.id) { blocked, _ ->
                        assertFalse(blocked, "An overridden blocker is not a blocker")
                    }
                }
            }
        }
    }

    @Test
    fun `A finished deployment leaves the slot unblocked`() {
        slotTestSupport.withFinishedDeployment { pipeline ->
            slotFlags(pipeline.slot.id) { blocked, _ ->
                assertFalse(blocked, "Nothing in flight, nothing blocked")
            }
        }
    }

    @Test
    fun `A slot with no upstream is never behind`() {
        slotTestSupport.withFinishedDeployment { pipeline ->
            slotFlags(pipeline.slot.id) { _, behind ->
                assertFalse(behind, "No upstream slot, so nothing to be behind of")
            }
        }
    }

    @Test
    fun `A slot is behind when the upstream one holds a newer build`() {
        withStagingAndProduction { staging, production ->
            // Same build deployed to both, then a newer one to staging only
            val branch = staging.project.branch()
            val build1 = branch.build()
            val build2 = branch.build()
            slotTestSupport.runAndFinishDeployment(slotService.startPipeline(staging, build1))
            slotTestSupport.runAndFinishDeployment(slotService.startPipeline(production, build1))
            slotTestSupport.runAndFinishDeployment(slotService.startPipeline(staging, build2))

            slotFlags(production.id) { _, behind ->
                assertTrue(behind, "Production is behind staging")
            }
            slotFlags(staging.id) { _, behind ->
                assertFalse(behind, "Staging is the head")
            }
        }
    }

    @Test
    fun `A slot which was never deployed is behind an upstream one which was`() {
        withStagingAndProduction { staging, production ->
            val branch = staging.project.branch()
            val build = branch.build()
            slotTestSupport.runAndFinishDeployment(slotService.startPipeline(staging, build))

            slotFlags(production.id) { _, behind ->
                assertTrue(behind, "Never deployed while staging holds a build")
            }
        }
    }

    @Test
    fun `A slot holding the same build as its upstream is not behind`() {
        withStagingAndProduction { staging, production ->
            val branch = staging.project.branch()
            val build = branch.build()
            slotTestSupport.runAndFinishDeployment(slotService.startPipeline(staging, build))
            slotTestSupport.runAndFinishDeployment(slotService.startPipeline(production, build))

            slotFlags(production.id) { _, behind ->
                assertFalse(behind, "Same build on both sides")
            }
        }
    }

    @Test
    fun `Next builds are the eligible builds newer than the deployed one`() {
        slotTestSupport.withSlot { slot ->
            val branch = slot.project.branch()
            val build1 = branch.build()
            val build2 = branch.build()
            val build3 = branch.build()
            slotTestSupport.runAndFinishDeployment(slotService.startPipeline(slot, build1))

            run(
                """
                    {
                        slotById(id: "${slot.id}") {
                            nextBuilds {
                                name
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertEquals(
                    listOf(build3.name, build2.name),
                    data.path("slotById").path("nextBuilds").values().map { it.path("name").asText() },
                    "Newest first, and only the ones newer than the deployed build"
                )
            }
        }
    }

    @Test
    fun `Next builds are limited by the count argument`() {
        slotTestSupport.withSlot { slot ->
            val branch = slot.project.branch()
            repeat(5) { branch.build() }

            run(
                """
                    {
                        slotById(id: "${slot.id}") {
                            nextBuilds(count: 2) {
                                name
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertEquals(
                    2,
                    data.path("slotById").path("nextBuilds").size(),
                    "Limited to the requested count"
                )
            }
        }
    }

    @Test
    fun `Next builds are empty when the slot already holds the newest build`() {
        slotTestSupport.withSlot { slot ->
            val branch = slot.project.branch()
            val build = branch.build()
            slotTestSupport.runAndFinishDeployment(slotService.startPipeline(slot, build))

            run(
                """
                    {
                        slotById(id: "${slot.id}") {
                            nextBuilds {
                                name
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertEquals(
                    0,
                    data.path("slotById").path("nextBuilds").size(),
                    "Nothing newer to move to"
                )
            }
        }
    }

    @Test
    fun `Next builds ignore the ones refused by an admission rule`() {
        slotTestSupport.withSlot { slot ->
            slotService.addAdmissionRuleConfig(
                SlotAdmissionRuleTestFixtures.testBranchPatternAdmissionRuleConfig(slot)
            )
            slot.project.branch(name = "main") { build() }
            val release = slot.project.branch(name = "release-1.0")
            val accepted = release.build()

            run(
                """
                    {
                        slotById(id: "${slot.id}") {
                            nextBuilds {
                                name
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertEquals(
                    listOf(accepted.name),
                    data.path("slotById").path("nextBuilds").values().map { it.path("name").asText() },
                    "Only the builds the rules accept"
                )
            }
        }
    }

    /**
     * Two slots of the same project, staging (order 1) below production (order 2), so that
     * production's parent in the slot graph is staging.
     */
    private fun withStagingAndProduction(code: (staging: net.nemerosa.ontrack.extension.environments.Slot, production: net.nemerosa.ontrack.extension.environments.Slot) -> Unit) {
        project {
            environmentTestSupport.withEnvironment(order = 1) { stagingEnv ->
                environmentTestSupport.withEnvironment(order = 2) { productionEnv ->
                    val staging = slotTestSupport.slot(project = project, environment = stagingEnv)
                    val production = slotTestSupport.slot(project = project, environment = productionEnv)
                    code(staging, production)
                }
            }
        }
    }

}
