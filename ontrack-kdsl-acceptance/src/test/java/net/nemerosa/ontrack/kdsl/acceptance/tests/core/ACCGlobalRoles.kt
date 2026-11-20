package net.nemerosa.ontrack.kdsl.acceptance.tests.core

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.acceptance.tests.support.uid
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLClientException
import net.nemerosa.ontrack.kdsl.spec.admin.admin
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Global roles add up (ADR 0023, #2162): granting a global role keeps the others, revoking one
 * keeps the others.
 *
 * The acceptance tests do not depend on `ontrack-model`, so the role IDs are spelled out here:
 * `Roles.GLOBAL_PARTICIPANT`, `Roles.GLOBAL_READ_ONLY` and `Roles.GLOBAL_CREATOR`.
 */
class ACCGlobalRoles : AbstractACCDSLTestSupport() {

    @Test
    fun `The global roles of an account add up`() {
        withNotGrantProjectViewToAll {
            val admin = ontrack.admin
            project {
                val project = this
                withUser { account ->
                    assertFalse(canSee(project.name), "No role, project not visible")
                    admin.grantGlobalRoleToAccount(account, PARTICIPANT)
                    admin.grantGlobalRoleToAccount(account, READ_ONLY)
                    assertEquals(listOf(PARTICIPANT, READ_ONLY), admin.getGlobalRoles(account))
                    admin.revokeGlobalRoleFromAccount(account, PARTICIPANT)
                    assertEquals(listOf(READ_ONLY), admin.getGlobalRoles(account))
                    assertTrue(canSee(project.name), "Still visible through the other role")
                    admin.revokeGlobalRoleFromAccount(account, READ_ONLY)
                    assertFalse(canSee(project.name), "No role left, project not visible")
                }
            }
        }
    }

    @Test
    fun `Granting and revoking the global roles of an account group`() {
        val admin = ontrack.admin
        val group = admin.createAccountGroup(uid("g"), "Release engineers")
        // Granting twice is a no-op, not an error
        admin.grantGlobalRoleToAccountGroup(group, PARTICIPANT)
        admin.grantGlobalRoleToAccountGroup(group, PARTICIPANT)
        admin.grantGlobalRoleToAccountGroup(group, CREATOR)
        assertEquals(listOf(CREATOR, PARTICIPANT), admin.getGlobalRoles(group))
        admin.revokeGlobalRoleFromAccountGroup(group, CREATOR)
        assertEquals(listOf(PARTICIPANT), admin.getGlobalRoles(group))
        admin.revokeGlobalRoleFromAccountGroup(group, PARTICIPANT)
        assertEquals(emptyList(), admin.getGlobalRoles(group))
    }

    /**
     * Looking for a project the current user may not see is refused.
     */
    private fun canSee(name: String): Boolean =
        try {
            ontrack.findProjectByName(name) != null
        } catch (ex: GraphQLClientException) {
            if (ex.message?.contains("Project access not granted") == true) {
                false
            } else {
                throw ex
            }
        }

    companion object {
        private const val PARTICIPANT = "PARTICIPANT"
        private const val READ_ONLY = "READ_ONLY"
        private const val CREATOR = "CREATOR"
    }
}
