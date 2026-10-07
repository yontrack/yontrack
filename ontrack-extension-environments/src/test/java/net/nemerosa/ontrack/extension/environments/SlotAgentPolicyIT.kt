package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.extension.environments.rules.core.ManualApprovalSlotAdmissionRule
import net.nemerosa.ontrack.extension.environments.rules.core.ManualApprovalSlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.rules.core.ManualApprovalSlotAdmissionRuleData
import net.nemerosa.ontrack.extension.environments.rules.core.ManualApprovalSlotAdmissionRuleException
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AgentPolicyException
import net.nemerosa.ontrack.model.security.Roles
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The agent policy on slots: an agent acts on the pipelines of a slot only when the slot admits
 * agents, and it never satisfies a manual approval - "agent asks, human approves".
 */
class SlotAgentPolicyIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private lateinit var owner: Account

    private fun agent(): AgentTestSupport.TestAgent {
        owner = asAdmin { doCreateAccountWithGlobalRole(Roles.GLOBAL_AUTOMATION) }
        return agentTestSupport.registerAgent(owner = owner)
    }

    private fun <T> AgentTestSupport.TestAgent.act(code: () -> T): T =
        agentTestSupport.withToken(token, code = code)

    private fun admittingSlot(): Slot {
        val slot = asAdmin { slotTestSupport.slot() }
        asAdmin {
            slotService.saveSlot(slot.withAgentsAdmitted(true))
        }
        return asAdmin { slotService.getSlotById(slot.id) }
    }

    @Test
    fun `A slot does not admit agents by default`() {
        val slot = asAdmin { slotTestSupport.slot() }
        assertFalse(asAdmin { slotService.getSlotById(slot.id) }.agentsAdmitted)
    }

    @Test
    fun `Saving whether a slot admits agents`() {
        val slot = admittingSlot()
        assertTrue(slot.agentsAdmitted)
        asAdmin {
            slotService.saveSlot(slot.withAgentsAdmitted(false))
            assertFalse(slotService.getSlotById(slot.id).agentsAdmitted)
        }
    }

    @Test
    fun `An agent cannot start a pipeline on a slot which does not admit agents`() {
        val agent = agent()
        asAdmin {
            slotTestSupport.withSlot { slot ->
                slot.project.branch {
                    build {
                        val ex = assertFailsWith<AgentPolicyException> {
                            agent.act { slotService.startPipeline(slot, this) }
                        }
                        assertEquals(
                            "agent ${agent.account.email} may not start a pipeline on ${slot.fullName()}: the slot does not admit agents (agent policy)",
                            ex.message
                        )
                        assertTrue(slotService.findPipelineByBuild(this).isEmpty(), "No pipeline")
                    }
                }
            }
        }
    }

    @Test
    fun `An agent cannot start a deployment on a slot which does not admit agents`() {
        val agent = agent()
        asAdmin {
            slotTestSupport.withSlotPipeline { pipeline ->
                assertFailsWith<AgentPolicyException> {
                    agent.act { slotService.runDeployment(pipeline.id, dryRun = false) }
                }
            }
        }
    }

    @Test
    fun `An agent cannot finish a deployment on a slot which does not admit agents`() {
        val agent = agent()
        asAdmin {
            slotTestSupport.withRunningDeployment { pipeline ->
                assertFailsWith<AgentPolicyException> {
                    agent.act { slotService.finishDeployment(pipeline.id) }
                }
            }
        }
    }

    @Test
    fun `An agent deploys on a slot which admits agents`() {
        val agent = agent()
        val slot = admittingSlot()
        asAdmin {
            slot.project.branch {
                build {
                    val pipeline = agent.act { slotService.startPipeline(slot, this) }
                    agent.act {
                        assertTrue(slotService.runDeployment(pipeline.id, dryRun = false).ok, "Running")
                        assertTrue(slotService.finishDeployment(pipeline.id).ok, "Deployed")
                    }
                    assertEquals(SlotPipelineStatus.DONE, slotService.findPipelineById(pipeline.id)?.status)
                }
            }
        }
    }

    @Test
    fun `An agent never overrides a rule, even on a slot which admits agents`() {
        val agent = agent()
        val slot = admittingSlot()
        asAdmin {
            slot.project.branch {
                build {
                    val pipeline = agent.act { slotService.startPipeline(slot, this) }
                    val ex = assertFailsWith<AgentPolicyException> {
                        agent.act {
                            slotService.finishDeployment(pipeline.id, forcing = true, message = "Forcing")
                        }
                    }
                    assertEquals(
                        "agent ${agent.account.email} may not SlotPipelineOverride (agent policy)",
                        ex.message
                    )
                }
            }
        }
    }

    @Test
    fun `Agent asks, human approves`() {
        val agent = agent()
        val slot = admittingSlot()
        asAdmin {
            val admissionRuleConfig = SlotAdmissionRuleConfig(
                slot = slot,
                name = "manualApproval",
                description = "Manual approval is required",
                ruleId = ManualApprovalSlotAdmissionRule.ID,
                // Even listed, the agent cannot approve
                ruleConfig = ManualApprovalSlotAdmissionRuleConfig(
                    message = "Approval required",
                    users = listOf(agent.account.email, owner.email),
                ).asJson()
            )
            slotService.addAdmissionRuleConfig(admissionRuleConfig)
            slot.project.branch {
                build {
                    // The agent asks
                    val pipeline = agent.act { slotService.startPipeline(slot, this) }
                    assertEquals(SlotPipelineStatus.CANDIDATE, pipeline.status)
                    // The agent cannot approve
                    val approval = ManualApprovalSlotAdmissionRuleData(approval = true, message = "OK").asJson()
                    val ex = assertFailsWith<ManualApprovalSlotAdmissionRuleException> {
                        agent.act { slotService.setupAdmissionRule(pipeline, admissionRuleConfig, approval) }
                    }
                    assertEquals("an agent cannot approve; ask ${owner.fullName}", ex.message)
                    // Still a candidate, not deployable
                    assertEquals(SlotPipelineStatus.CANDIDATE, slotService.findPipelineById(pipeline.id)?.status)
                    assertFalse(
                        agent.act { slotService.runDeployment(pipeline.id, dryRun = true) }.ok,
                        "Not deployable without approval"
                    )
                    // A human approves
                    asFixedAccount(owner) {
                        slotService.setupAdmissionRule(pipeline, admissionRuleConfig, approval)
                    }
                    // The agent deploys
                    agent.act {
                        assertTrue(slotService.runDeployment(pipeline.id, dryRun = false).ok, "Running")
                        assertTrue(slotService.finishDeployment(pipeline.id).ok, "Deployed")
                    }
                    assertEquals(SlotPipelineStatus.DONE, slotService.findPipelineById(pipeline.id)?.status)
                }
            }
        }
    }
}
