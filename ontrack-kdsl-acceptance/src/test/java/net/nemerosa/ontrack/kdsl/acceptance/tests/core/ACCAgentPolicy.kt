package net.nemerosa.ontrack.kdsl.acceptance.tests.core

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.SlotPipelineStatus
import net.nemerosa.ontrack.kdsl.spec.ReadinessItem
import net.nemerosa.ontrack.kdsl.spec.ReadinessKind
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.admin.Account
import net.nemerosa.ontrack.kdsl.spec.admin.agentPolicy
import net.nemerosa.ontrack.kdsl.spec.admin.agents
import net.nemerosa.ontrack.kdsl.spec.extension.environments.environments
import net.nemerosa.ontrack.kdsl.spec.setProperty
import net.nemerosa.ontrack.kdsl.spec.withToken
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The agent policy (#2026): an agent gets the rights of its owner, narrowed to recording evidence,
 * promoting on the levels and deploying in the slots which admit agents - and it never approves.
 * The agent reads its policy before acting (#2027).
 */
class ACCAgentPolicy : AbstractACCDSLTestSupport() {

    /**
     * Registers an agent for a new user with the automation role, and returns its owner and a client
     * acting as the agent.
     */
    private fun agent(): Pair<Account, Ontrack> {
        lateinit var owner: Account
        lateinit var token: String
        withUser(globalRole = "AUTOMATION") { account ->
            owner = account
            token = ontrack.agents.register(
                slug = uid("acc-").lowercase(),
                displayName = "Claude",
                tool = "Claude Code",
            ).generateToken("ci")
        }
        return owner to ontrack.withToken(token)
    }

    private fun expectFailure(code: () -> Unit): String {
        try {
            code()
        } catch (any: Exception) {
            return any.message ?: ""
        }
        fail("Expected a refusal")
    }

    @Test
    fun `Agent asks, human approves`() {
        val (_, asAgent) = agent()
        project {
            branch {
                val build = build { this }
                val environment = ontrack.environments.createEnvironment(name = uid("env-"), order = 0)
                val slot = environment.createSlot(project = project)
                slot.addAdmissionRule(
                    ruleId = "manual",
                    ruleConfig = mapOf("message" to "Approval required").asJson(),
                )
                assertEquals(true, slot.update(agentsAdmitted = true).agentsAdmitted)

                // The agent asks
                val agentSlot = asAgent.environments.getSlot(environment.name, project.name)
                val candidate = agentSlot.createPipeline(build)
                assertEquals(SlotPipelineStatus.CANDIDATE, candidate.status)

                // The agent cannot approve
                val agentPipeline = assertNotNull(asAgent.environments.findPipelineById(candidate.id))
                val refusal = expectFailure { agentPipeline.manualApproval("I approve myself") }
                assertTrue(refusal.contains("an agent cannot approve; ask"), "Refused: $refusal")
                assertEquals(
                    SlotPipelineStatus.CANDIDATE,
                    ontrack.environments.findPipelineById(candidate.id)?.status,
                    "Still a candidate"
                )

                // A human approves
                assertNotNull(ontrack.environments.findPipelineById(candidate.id)).manualApproval("Go")

                // The agent deploys
                agentPipeline.startDeploying()
                agentPipeline.finishDeployment()
                assertEquals(
                    SlotPipelineStatus.DONE,
                    ontrack.environments.findPipelineById(candidate.id)?.status,
                )
            }
        }
    }

    @Test
    fun `An agent cannot start a pipeline in a slot which does not admit agents`() {
        val (_, asAgent) = agent()
        project {
            branch {
                val build = build { this }
                val environment = ontrack.environments.createEnvironment(name = uid("env-"), order = 0)
                environment.createSlot(project = project)
                val agentSlot = asAgent.environments.getSlot(environment.name, project.name)
                val refusal = expectFailure { agentSlot.createPipeline(build) }
                assertTrue(refusal.contains("the slot does not admit agents (agent policy)"), "Refused: $refusal")
            }
        }
    }

    @Test
    fun `An agent records evidence and promotes only on a level which admits agents`() {
        val (_, asAgent) = agent()
        project {
            branch {
                validationStamp("CI")
                promotion("SILVER")
                promotion("GOLD").setProperty(
                    "net.nemerosa.ontrack.extension.general.AgentsAdmittedPropertyType",
                    mapOf("admitted" to true),
                )
                val agentBranch = assertNotNull(asAgent.findBranchByName(project.name, name))
                // Recording evidence
                val build = agentBranch.createBuild(uid("b-"))
                build.validate("CI", status = "PASSED")
                // Promoting
                val refusal = expectFailure { build.promote("SILVER") }
                assertTrue(
                    refusal.contains("may not promote to SILVER: the promotion level does not admit agents (agent policy)"),
                    "Refused: $refusal"
                )
                build.promote("GOLD")
                assertEquals(
                    1,
                    ontrack.findBuildByName(project.name, name, build.name)
                        ?.getPromotionRunsForPromotionLevel("GOLD")?.size,
                )
            }
        }
    }

    @Test
    fun `An agent reads its policy, then promotes on the admitted level only`() {
        val (owner, asAgent) = agent()
        project {
            branch {
                validationStamp("CI")
                promotion("SILVER")
                promotion("GOLD").setProperty(
                    "net.nemerosa.ontrack.extension.general.AgentsAdmittedPropertyType",
                    mapOf("admitted" to true),
                )
                // A person has no agent policy
                assertNull(ontrack.agentPolicy(project.name))

                // The agent reads its policy first
                val policy = assertNotNull(asAgent.agentPolicy(project.name, branch = name))
                assertEquals("Test ${owner.email}", policy.owner)
                assertTrue(policy.canRecordEvidence, "The agent may record evidence")
                assertEquals(listOf(name to "GOLD"), policy.promotionLevels.map { it.branch to it.name })
                assertTrue(policy.slots.isEmpty(), "No slot")

                // The agent records evidence...
                val agentBranch = assertNotNull(asAgent.findBranchByName(project.name, name))
                val build = agentBranch.createBuild(uid("b-"))
                build.validate("CI", status = "PASSED")
                // ... learns why it cannot promote to SILVER, without trying...
                assertEquals(
                    ReadinessItem(
                        kind = ReadinessKind.AGENT_POLICY,
                        name = "SILVER",
                        message = "agents are not admitted on SILVER; ask Test ${owner.email}",
                    ),
                    build.readiness(promotionLevel = "SILVER").missing.single { it.kind == ReadinessKind.AGENT_POLICY },
                )
                // ... and promotes only on the levels of its policy
                policy.promotionLevels.forEach { build.promote(it.name) }

                val promoted = assertNotNull(ontrack.findBuildByName(project.name, name, build.name))
                assertEquals(1, promoted.getPromotionRunsForPromotionLevel("GOLD").size, "Promoted to GOLD")
                assertTrue(promoted.getPromotionRunsForPromotionLevel("SILVER").isEmpty(), "Not promoted to SILVER")
            }
        }
    }
}
