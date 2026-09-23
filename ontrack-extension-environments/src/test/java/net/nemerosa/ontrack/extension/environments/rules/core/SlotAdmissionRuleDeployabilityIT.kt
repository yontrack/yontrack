package net.nemerosa.ontrack.extension.environments.rules.core

import net.nemerosa.ontrack.extension.environments.*
import net.nemerosa.ontrack.extension.environments.rules.SlotAdmissionRuleRegistry
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Build
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/**
 * Every admission rule answers "can this build go to the slot now?" twice: once in SQL, to list
 * the deployable builds of a slot ([SlotAdmissionRule.fillEligibilityCriteria] with
 * `deployable = true`), and once in Kotlin, to say it of one build and why not
 * ([SlotAdmissionRule.checkBuildDeployable]). Nothing but these tests keeps the two readings from
 * drifting, and a drift is exactly the bug of #1851: a build listed as ready which cannot be run.
 *
 * For each rule, a build is in the deployable listing ⇔ its check is ok, or decided on the pipeline
 * only (`null`).
 */
@AsAdminTest
class SlotAdmissionRuleDeployabilityIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var slotAdmissionRuleRegistry: SlotAdmissionRuleRegistry

    @Test
    fun `Promotion rule`() {
        slotTestSupport.withSlot { slot ->
            val branch = slot.project.branch()
            val bronze = branch.promotionLevel("BRONZE")
            val silver = branch.promotionLevel("SILVER")
            slotService.addAdmissionRuleConfig(
                SlotAdmissionRuleTestFixtures.testPromotionAdmissionRuleConfig(slot, promotion = bronze.name)
            )

            val promoted = branch.build { promote(bronze) }
            val notPromoted = branch.build()
            // Promoted, but not to the level the rule asks for
            val promotedElsewhere = branch.build { promote(silver) }

            assertDeployability(slot, promoted, expected = true)
            assertDeployability(slot, notPromoted, expected = false, reason = "Build not promoted")
            assertDeployability(slot, promotedElsewhere, expected = false, reason = "Build not promoted")
        }
    }

    @Test
    fun `Branch pattern rule with last branch only`() {
        slotTestSupport.withSlot { slot ->
            slotService.addAdmissionRuleConfig(
                SlotAdmissionRuleConfig(
                    slot = slot,
                    name = "lastRelease",
                    description = null,
                    ruleId = BranchPatternSlotAdmissionRule.ID,
                    ruleConfig = BranchPatternSlotAdmissionRuleConfig(
                        lastBranchOnly = true,
                        includes = listOf("release-.*"),
                        // The excluded branch is the "highest" one: it must not count as the last
                        excludes = listOf("release-5\\..*"),
                    ).asJson()
                )
            )
            val older = slot.project.branch("release-4.10").build()
            val last = slot.project.branch("release-4.11").build()
            slot.project.branch("release-5.0").build()

            assertDeployability(slot, last, expected = true)
            assertDeployability(slot, older, expected = false, reason = "Build branch is not valid")
        }
    }

    @Test
    fun `Environment rule`() {
        slotTestSupport.withSlot { previousSlot ->
            slotTestSupport.withSlot(project = previousSlot.project) { slot ->
                slotService.addAdmissionRuleConfig(
                    SlotAdmissionRuleTestFixtures.testEnvironmentAdmissionRuleConfig(slot, previousSlot)
                )
                val branch = slot.project.branch()
                val notDeployed = branch.build()
                val deployed = branch.build()
                slotTestSupport.runAndFinishDeployment(slotService.startPipeline(previousSlot, deployed))

                assertDeployability(slot, deployed, expected = true)
                assertDeployability(slot, notDeployed, expected = false)
            }
        }
    }

    @Test
    fun `Environment rule when the build was deployed but the previous slot has moved on`() {
        slotTestSupport.withSlot { previousSlot ->
            slotTestSupport.withSlot(project = previousSlot.project) { slot ->
                slotService.addAdmissionRuleConfig(
                    SlotAdmissionRuleTestFixtures.testEnvironmentAdmissionRuleConfig(slot, previousSlot)
                )
                val branch = slot.project.branch()
                val superseded = branch.build()
                slotTestSupport.runAndFinishDeployment(slotService.startPipeline(previousSlot, superseded))
                // A newer build is now on its way into the previous slot
                val newer = branch.build()
                slotService.startPipeline(previousSlot, newer)

                assertDeployability(slot, superseded, expected = false)
                assertDeployability(slot, newer, expected = false)
            }
        }
    }

    @Test
    fun `Manual approval rule is decided on the pipeline only`() {
        slotTestSupport.withSlot { slot ->
            val config = SlotAdmissionRuleTestFixtures.testManualApprovalRuleConfig(slot)
            slotService.addAdmissionRuleConfig(config)
            val build = slot.project.branch().build()

            assertEquals(null, check(config, build), "Manual approval cannot be decided on a build")
            assertDeployability(slot, build, expected = true)

            val eligibleSlot = slotService.getEligibleSlotsForBuild(build).single { it.slot.id == slot.id }
            assertEquals(listOf(config.id), eligibleSlot.pipelineOnlyRules.map { it.id })
        }
    }

    private fun assertDeployability(slot: Slot, build: Build, expected: Boolean, reason: String? = null) {
        val listed = slotService.getEligibleBuilds(slot, count = 100, deployable = true).pageItems
            .any { it.id == build.id }
        assertEquals(expected, listed, "Build ${build.name} in the deployable listing")

        val checks = slotService.getAdmissionRuleConfigs(slot).map { check(it, build) }
        assertEquals(
            expected,
            checks.all { it == null || it.ok },
            "Build ${build.name} passes the deployability checks"
        )

        val eligibleSlot = slotService.getEligibleSlotsForBuild(build).single { it.slot.id == slot.id }
        assertEquals(true, eligibleSlot.eligible, "Build ${build.name} eligible")
        assertEquals(expected, eligibleSlot.deployable, "Build ${build.name} deployable on the eligible slot")
        if (expected) {
            assertEquals(emptyList(), eligibleSlot.nonDeployableRules)
        } else {
            assertEquals(1, eligibleSlot.nonDeployableRules.size)
            if (reason != null) {
                assertEquals(reason, eligibleSlot.nonDeployableRules.single().reason)
            }
        }
    }

    private fun check(config: SlotAdmissionRuleConfig, build: Build): SlotDeploymentCheck? =
        check(slotAdmissionRuleRegistry.getRule(config.ruleId), config, build)

    private fun <C : Any, D> check(
        rule: SlotAdmissionRule<C, D>,
        config: SlotAdmissionRuleConfig,
        build: Build,
    ): SlotDeploymentCheck? =
        rule.checkBuildDeployable(build, config.slot, rule.parseConfig(config.ruleConfig))

}
