package net.nemerosa.ontrack.extension.environments

import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AgentTestSupport
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.security.ActorAgentSession
import net.nemerosa.ontrack.model.structure.SignatureActor
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The `ACTOR` column of the changes of a slot pipeline.
 *
 * The agent policy is not the point here (see AgentPolicyIT): the actions of the agent are run by
 * the system on its behalf, which keeps them as the user and the actor of the change.
 */
@QueueNoAsync
class SlotPipelineChangeActorIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Autowired
    private lateinit var slotService: SlotService

    @Autowired
    private lateinit var agentTestSupport: AgentTestSupport

    private fun changeActors(pipeline: SlotPipeline): List<String?> =
        namedParameterJdbcTemplate.queryForList(
            "SELECT ACTOR FROM ENV_SLOT_PIPELINE_CHANGE WHERE PIPELINE_ID = :id ORDER BY TIMESTAMP",
            mapOf("id" to pipeline.id),
            String::class.java
        )

    @Test
    fun `The changes of a pipeline made by an agent carry the agent and its session`() {
        val agent = agentTestSupport.registerAgent(
            owner = asAdmin { doCreateAccount() },
            displayName = "Claude",
            tool = "Claude Code",
        )
        asAdmin {
            slotTestSupport.withSlot { slot ->
                slot.project.branch {
                    build {
                        val pipeline = agentTestSupport.withToken(agent.token, SESSION_ID, SESSION_LINK) {
                            securityService.asAdmin {
                                slotService.startPipeline(slot, this).also {
                                    slotService.cancelPipeline(it, "Not needed")
                                }
                            }
                        }
                        val expected = SignatureActor(
                            agent = agent.account.email,
                            displayName = "Claude",
                            tool = "Claude Code",
                            owner = agent.account.owner!!.email,
                            session = ActorAgentSession(id = SESSION_ID, link = SESSION_LINK),
                        )
                        val changes = slotService.getPipelineChanges(pipeline)
                        assertEquals(2, changes.size)
                        changes.forEach { change ->
                            assertEquals(agent.account.email, change.user)
                            assertEquals(expected, change.actor)
                        }
                        assertEquals(
                            listOf(
                                """{"kind":"agent","agent":"${agent.account.email}","displayName":"Claude","tool":"Claude Code","owner":"${agent.account.owner!!.email}","session":{"id":"$SESSION_ID","link":"$SESSION_LINK"}}""".parseAsJson(),
                            ),
                            changeActors(pipeline).map { it?.parseAsJson() }.distinct(),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `The changes of a pipeline made by a person have no actor`() {
        asAdmin {
            slotTestSupport.withSlotPipeline { pipeline ->
                slotService.cancelPipeline(pipeline, "Not needed")
                val changes = slotService.getPipelineChanges(pipeline)
                assertEquals(2, changes.size)
                changes.forEach { change -> assertNull(change.actor) }
                assertEquals(listOf<String?>(null, null), changeActors(pipeline))
            }
        }
    }

    companion object {
        private const val SESSION_ID = "session-2025"
        private const val SESSION_LINK = "https://claude.ai/code/session-2025"
    }
}
