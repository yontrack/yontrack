package net.nemerosa.ontrack.kdsl.acceptance.tests.core

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.spec.Ontrack
import net.nemerosa.ontrack.kdsl.spec.admin.agents
import net.nemerosa.ontrack.kdsl.spec.admin.currentAccount
import net.nemerosa.ontrack.kdsl.spec.generateToken
import net.nemerosa.ontrack.kdsl.spec.withToken
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A read-only token reads what its owner can read, and changes nothing (#2129).
 */
class ACCReadOnlyTokens : AbstractACCDSLTestSupport() {

    private fun assertWriteRefused(client: Ontrack) {
        val name = uid("p_")
        val message = try {
            client.createProject(name, "")
            fail("Expected a refusal")
        } catch (any: Exception) {
            any.message ?: ""
        }
        assertTrue("may not run a mutation" in message, message)
        assertNull(ontrack.findProjectByName(name), "Nothing was created")
    }

    @Test
    fun `A read-only token reads, and is refused on a write`() {
        val projectName = project { name }
        lateinit var token: String
        var ownerSeesProject = false
        lateinit var email: String
        withUser(globalRole = "AUTOMATION") { account ->
            email = account.email
            ownerSeesProject = ontrack.findProjectByName(projectName) != null
            token = ontrack.generateToken("ro", readOnly = true)
        }

        val client = ontrack.withToken(token)
        assertEquals(email, assertNotNull(client.currentAccount()).email)
        assertTrue(ownerSeesProject)
        assertNotNull(client.findProjectByName(projectName), "Reads what its owner can")
        assertWriteRefused(client)
    }

    @Test
    fun `The read-only token of an agent reads, and is refused on a write`() {
        val projectName = project { name }
        val slug = uid("acc-").lowercase()
        lateinit var token: String
        lateinit var fullToken: String
        withUser(globalRole = "AUTOMATION") { _ ->
            val agent = ontrack.agents.register(
                slug = slug,
                displayName = "Read-only agent",
                tool = "Claude Code",
            )
            fullToken = agent.generateToken("ci")
            token = agent.generateToken("ro", readOnly = true)
        }

        val client = ontrack.withToken(token)
        assertEquals("$slug[agent]", assertNotNull(client.currentAccount()).email)
        assertNotNull(client.findProjectByName(projectName), "Reads what its owner can")
        assertWriteRefused(client)
        // The full token of the same agent is not concerned
        assertNotNull(ontrack.withToken(fullToken).currentAccount())
    }

}
