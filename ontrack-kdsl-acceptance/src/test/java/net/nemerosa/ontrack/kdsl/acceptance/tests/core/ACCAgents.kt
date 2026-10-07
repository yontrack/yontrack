package net.nemerosa.ontrack.kdsl.acceptance.tests.core

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.admin.Account
import net.nemerosa.ontrack.kdsl.spec.admin.Agent
import net.nemerosa.ontrack.kdsl.spec.admin.admin
import net.nemerosa.ontrack.kdsl.spec.admin.agents
import net.nemerosa.ontrack.kdsl.spec.admin.currentAccount
import net.nemerosa.ontrack.kdsl.spec.withToken
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Registered agents: registration by their owner, tokens, and the lifecycle of the owner (#2024).
 */
class ACCAgents : AbstractACCDSLTestSupport() {

    private fun slug() = uid("acc-").lowercase()

    @Test
    fun `An agent registered by its owner calls the API with its own token`() {
        val slug = slug()
        val projectName = project { name }
        lateinit var owner: Account
        lateinit var token: String
        var ownerSeesProject = false
        withUser { account ->
            owner = account
            ownerSeesProject = ontrack.findProjectByName(projectName) != null
            val agent = ontrack.agents.register(
                slug = slug,
                displayName = "Claude Code for the acceptance tests",
                tool = "Claude Code",
            )
            assertEquals("$slug[agent]", agent.email)
            assertEquals(account.email, agent.owner)
            token = agent.generateToken("ci")
        }

        // Acting as the agent
        val asAgent = ontrack.withToken(token)
        val current = assertNotNull(asAgent.currentAccount(), "The agent token authenticates")
        assertEquals("$slug[agent]", current.email)
        assertEquals("AGENT", current.kind)
        assertEquals(owner.email, current.owner)
        // The rights of its owner (#2026)
        assertEquals(
            ownerSeesProject,
            asAgent.findProjectByName(projectName) != null,
            "An agent sees what its owner sees"
        )

        // Deleting the owner deletes the agent, and its token stops working
        ontrack.admin.deleteAccount(owner)
        assertNull(
            runCatching { asAgent.currentAccount() }.getOrNull(),
            "The token of the agent no longer works"
        )
        assertTrue(ontrack.agents.list().none { it.email == "$slug[agent]" }, "The agent is deleted")
    }

    @Test
    fun `An administrator transfers and deletes an agent`() {
        val first = ontrack.admin.createUser(email = "${uid("u-")}@ontrack.local")
        val second = ontrack.admin.createUser(email = "${uid("u-")}@ontrack.local")
        val agent: Agent = ontrack.agents.register(slug = slug(), tool = "Codex", owner = first.email)
        assertEquals(first.email, agent.owner)

        val transferred = agent.transfer(second.email)
        assertEquals(second.email, transferred.owner)
        assertEquals(listOf(agent.email), ontrack.agents.list(owner = second.email).map { it.email })

        val token = agent.generateToken("ci")
        transferred.delete()
        assertNull(ontrack.agents.findById(agent.id), "Agent deleted")
        assertNull(runCatching { ontrack.withToken(token).currentAccount() }.getOrNull(), "Token gone")
    }
}
