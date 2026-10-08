package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.extension.environments.rules.core.ManualApprovalSlotAdmissionRule
import net.nemerosa.ontrack.extension.environments.rules.core.ManualApprovalSlotAdmissionRuleConfig
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An agent reads its own policy (#2027): the slots of `user.agentPolicy`.
 */
class AgentPolicySlotsGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private fun agentOf(ownerGlobalRole: String): AgentTestSupport.TestAgent =
        agentTestSupport.registerAgent(owner = asAdmin { doCreateAccountWithGlobalRole(ownerGlobalRole) })

    private fun slot(project: Project, order: Int, admitted: Boolean, manual: Boolean = false): Slot =
        asAdmin {
            val slot = slotTestSupport.slot(order = order, project = project)
            slotService.saveSlot(slot.withAgentsAdmitted(admitted))
            if (manual) {
                slotService.addAdmissionRuleConfig(
                    SlotAdmissionRuleConfig(
                        slot = slot,
                        name = "approval",
                        description = null,
                        ruleId = ManualApprovalSlotAdmissionRule.ID,
                        ruleConfig = ManualApprovalSlotAdmissionRuleConfig(message = "Approval required").asJson(),
                    )
                )
            }
            slotService.getSlotById(slot.id)
        }

    private fun AgentTestSupport.TestAgent.slots(project: Project): JsonNode =
        agentTestSupport.withToken(token) {
            run(
                """
                    query AgentPolicy(${'$'}project: String!) {
                        user {
                            agentPolicy(project: ${'$'}project) {
                                slots {
                                    id
                                    environment
                                    qualifier
                                    manualApproval
                                }
                            }
                        }
                    }
                """,
                mapOf("project" to project.name)
            )
        }.path("user").path("agentPolicy").path("slots")

    @Test
    fun `The policy of an agent lists the slots which admit agents, and flags the manual approvals`() {
        val agent = agentOf(Roles.GLOBAL_AUTOMATION)
        val project = asAdmin { project() }
        val staging = slot(project, order = 10, admitted = true)
        val production = slot(project, order = 20, admitted = true, manual = true)
        slot(project, order = 30, admitted = false)

        val slots = agent.slots(project)
        assertEquals(
            listOf(
                listOf(staging.id, staging.environment.name, "", "false"),
                listOf(production.id, production.environment.name, "", "true"),
            ),
            slots.toList().map {
                listOf(
                    it.path("id").asString(),
                    it.path("environment").asString(),
                    it.path("qualifier").asString(),
                    it.path("manualApproval").asBoolean().toString(),
                )
            }
        )
    }

    @Test
    fun `The slots of the policy of an agent reflect the rights of its owner`() {
        val agent = agentOf(Roles.GLOBAL_READ_ONLY)
        val project = asAdmin { project() }
        slot(project, order = 10, admitted = true)
        assertTrue(agent.slots(project).isEmpty, "The owner cannot start a pipeline: no slot")
    }
}
