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
 * Project roles add up (ADR 0023, #2161): granting a role keeps the others, revoking one keeps
 * the others.
 *
 * The acceptance tests do not depend on `ontrack-model`, so the role IDs are spelled out here:
 * `Roles.PROJECT_PARTICIPANT` and `Roles.PROJECT_READ_ONLY`.
 */
class ACCProjectRoles : AbstractACCDSLTestSupport() {

    @Test
    fun `The project roles of an account add up`() {
        withNotGrantProjectViewToAll {
            val admin = ontrack.admin
            project {
                val project = this
                withUser { account ->
                    assertFalse(canSee(project.name), "No role, project not visible")
                    admin.grantProjectRoleToAccount(project, account, PARTICIPANT)
                    admin.grantProjectRoleToAccount(project, account, READ_ONLY)
                    admin.revokeProjectRoleFromAccount(project, account, PARTICIPANT)
                    assertTrue(canSee(project.name), "Still visible through the other role")
                    admin.revokeProjectRoleFromAccount(project, account, READ_ONLY)
                    assertFalse(canSee(project.name), "No role left, project not visible")
                }
            }
        }
    }

    @Test
    fun `Granting and revoking the project roles of an account group`() {
        project {
            val name = uid("g")
            val group = ontrack.admin.createAccountGroup(name, "Release engineers")
            assertEquals(group, ontrack.admin.findAccountGroupByName(name))
            // Granting twice is a no-op, not an error
            ontrack.admin.grantProjectRoleToAccountGroup(this, group, PARTICIPANT)
            ontrack.admin.grantProjectRoleToAccountGroup(this, group, PARTICIPANT)
            ontrack.admin.grantProjectRoleToAccountGroup(this, group, READ_ONLY)
            ontrack.admin.revokeProjectRoleFromAccountGroup(this, group, PARTICIPANT)
            ontrack.admin.revokeProjectRoleFromAccountGroup(this, group, READ_ONLY)
        }
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
    }
}
