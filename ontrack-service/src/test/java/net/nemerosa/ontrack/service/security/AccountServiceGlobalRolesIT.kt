package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.dashboards.DashboardEdition
import net.nemerosa.ontrack.model.exceptions.AccountGroupNotFoundException
import net.nemerosa.ontrack.model.exceptions.AccountNotFoundException
import net.nemerosa.ontrack.model.exceptions.RoleNotFoundException
import net.nemerosa.ontrack.model.security.*
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Global roles add up (ADR 0023): an account or a group holds several global roles, and gets the
 * union of their functions.
 */
class AccountServiceGlobalRolesIT : AbstractDSLTestSupport() {

    @Test
    fun `A group with two global roles has the functions of both`() {
        withNoGrantViewToAll {
            asAdmin {
                val group = doCreateAccountGroup()
                accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_CREATOR)
                accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_PARTICIPANT)
                val account = doCreateAccount(group)
                project {
                    asFixedAccount(account) {
                        // From Creator only
                        assertTrue(securityService.isGlobalFunctionGranted(ProjectCreation::class.java))
                        assertTrue(securityService.isProjectFunctionGranted(this, BranchCreate::class.java))
                        // From Participant only
                        assertTrue(securityService.isGlobalFunctionGranted(DashboardEdition::class.java))
                        assertTrue(securityService.isProjectFunctionGranted(this, ValidationRunStatusChange::class.java))
                        // From neither
                        assertFalse(securityService.isGlobalFunctionGranted(GlobalSettings::class.java))
                        assertFalse(securityService.isProjectFunctionGranted(this, ProjectDelete::class.java))
                    }
                }
            }
        }
    }

    @Test
    fun `An account with two global roles has the functions of both`() {
        withNoGrantViewToAll {
            asAdmin {
                val account = doCreateAccount()
                accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_CREATOR)
                accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_CONTROLLER)
                project {
                    asFixedAccount(account) {
                        assertTrue(securityService.isGlobalFunctionGranted(ProjectCreation::class.java))
                        assertTrue(securityService.isProjectFunctionGranted(this, PromotionRunCreate::class.java))
                        assertFalse(securityService.isProjectFunctionGranted(this, ProjectDelete::class.java))
                    }
                }
            }
        }
    }

    @Test
    fun `Revoking one global role of a group keeps the other`() {
        withNoGrantViewToAll {
            asAdmin {
                val group = doCreateAccountGroup()
                accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_CREATOR)
                accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_PARTICIPANT)
                accountService.revokeGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_CREATOR)
                assertEquals(listOf(Roles.GLOBAL_PARTICIPANT), accountService.getGlobalRolesForAccountGroup(group).map { it.id })
                val account = doCreateAccount(group)
                project {
                    asFixedAccount(account) {
                        assertFalse(securityService.isGlobalFunctionGranted(ProjectCreation::class.java))
                        assertTrue(securityService.isProjectFunctionGranted(this, ValidationRunStatusChange::class.java))
                    }
                }
            }
        }
    }

    @Test
    fun `Revoking one global role of an account keeps the other`() {
        asAdmin {
            val account = doCreateAccount()
            accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_CREATOR)
            accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_CONTROLLER)
            accountService.revokeGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_CONTROLLER)
            assertEquals(listOf(Roles.GLOBAL_CREATOR), accountService.getGlobalRolesForAccount(account).map { it.id })
        }
    }

    @Test
    fun `Granting a global role twice is a no-op`() {
        asAdmin {
            val group = doCreateAccountGroup()
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_PARTICIPANT)
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_PARTICIPANT)
            assertEquals(listOf(Roles.GLOBAL_PARTICIPANT), accountService.getGlobalRolesForAccountGroup(group).map { it.id })
        }
    }

    @Test
    fun `Global permissions list one entry per target and role`() {
        asAdmin {
            val group = doCreateAccountGroup()
            val account = doCreateAccount()
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_CREATOR)
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_PARTICIPANT)
            accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_CONTROLLER)
            accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_READ_ONLY)
            assertEquals(
                listOf(Roles.GLOBAL_CREATOR, Roles.GLOBAL_PARTICIPANT),
                globalPermissionsOf(group.asPermissionTarget())
            )
            assertEquals(
                listOf(Roles.GLOBAL_CONTROLLER, Roles.GLOBAL_READ_ONLY),
                globalPermissionsOf(account.asPermissionTarget())
            )
        }
    }

    @Test
    fun `Saving a global permission replaces all the global roles`() {
        asAdmin {
            val group = doCreateAccountGroup()
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_CREATOR)
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_PARTICIPANT)
            accountService.saveGlobalPermission(PermissionTargetType.GROUP, group.id(), PermissionInput(Roles.GLOBAL_READ_ONLY))
            assertEquals(listOf(Roles.GLOBAL_READ_ONLY), globalPermissionsOf(group.asPermissionTarget()))
        }
    }

    @Test
    fun `Granting or revoking an unknown global role is refused`() {
        asAdmin {
            val group = doCreateAccountGroup()
            assertFailsWith<RoleNotFoundException> {
                accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), "UNKNOWN_ROLE")
            }
            assertFailsWith<RoleNotFoundException> {
                accountService.revokeGlobalPermission(PermissionTargetType.GROUP, group.id(), "UNKNOWN_ROLE")
            }
        }
    }

    @Test
    fun `Granting a global role to an unknown group or account is refused`() {
        asAdmin {
            assertFailsWith<AccountGroupNotFoundException> {
                accountService.grantGlobalPermission(PermissionTargetType.GROUP, Int.MAX_VALUE, Roles.GLOBAL_PARTICIPANT)
            }
            assertFailsWith<AccountNotFoundException> {
                accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, Int.MAX_VALUE, Roles.GLOBAL_PARTICIPANT)
            }
        }
    }

    @Test
    fun `Granting and revoking a global role needs the management of accounts or of groups`() {
        val group = asAdmin { doCreateAccountGroup() }
        val account = asAdmin { doCreateAccount() }
        asUser().call {
            assertFailsWith<AccessDeniedException> {
                accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_PARTICIPANT)
            }
            assertFailsWith<AccessDeniedException> {
                accountService.revokeGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_PARTICIPANT)
            }
        }
        asUser().with(AccountGroupManagement::class.java).call {
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_PARTICIPANT)
        }
        asUser().with(AccountManagement::class.java).call {
            accountService.grantGlobalPermission(PermissionTargetType.ACCOUNT, account.id(), Roles.GLOBAL_PARTICIPANT)
        }
        asAdmin {
            assertEquals(listOf(Roles.GLOBAL_PARTICIPANT), globalPermissionsOf(group.asPermissionTarget()))
            assertEquals(listOf(Roles.GLOBAL_PARTICIPANT), globalPermissionsOf(account.asPermissionTarget()))
        }
    }

    private fun globalPermissionsOf(target: PermissionTarget): List<String> =
        accountService.globalPermissions
            .filter { it.target.type == target.type && it.target.id == target.id }
            .map { it.role.id }
            .sorted()
}
