package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.exceptions.AccountGroupNotFoundException
import net.nemerosa.ontrack.model.exceptions.AccountNotFoundException
import net.nemerosa.ontrack.model.exceptions.ProjectNotFoundException
import net.nemerosa.ontrack.model.exceptions.RoleNotFoundException
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Project roles add up (ADR 0023): an account or a group holds several roles on a project, and
 * gets the union of their functions.
 */
class AccountServiceProjectRolesIT : AbstractDSLTestSupport() {

    @Test
    fun `Granting a project role twice to a group is a no-op`() {
        asAdmin {
            project {
                val group = doCreateAccountGroup()
                accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
                accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
                assertEquals(
                    listOf(Roles.PROJECT_PARTICIPANT),
                    permissionsOf(this, group.asPermissionTarget())
                )
            }
        }
    }

    @Test
    fun `Project permissions list one entry per target and role`() {
        asAdmin {
            project {
                val group = doCreateAccountGroup()
                val account = doCreateAccount()
                accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
                accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_VALIDATION_MANAGER)
                accountService.grantProjectPermission(id, PermissionTargetType.ACCOUNT, account.id(), Roles.PROJECT_PROMOTER)
                accountService.grantProjectPermission(id, PermissionTargetType.ACCOUNT, account.id(), Roles.PROJECT_READ_ONLY)
                assertEquals(
                    listOf(Roles.PROJECT_PARTICIPANT, Roles.PROJECT_VALIDATION_MANAGER),
                    permissionsOf(this, group.asPermissionTarget())
                )
                assertEquals(
                    listOf(Roles.PROJECT_PROMOTER, Roles.PROJECT_READ_ONLY),
                    permissionsOf(this, account.asPermissionTarget())
                )
            }
        }
    }

    @Test
    fun `Revoking one project role of a group keeps the other`() {
        asAdmin {
            project {
                val group = doCreateAccountGroup()
                accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
                accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_VALIDATION_MANAGER)
                accountService.revokeProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
                assertEquals(
                    listOf(Roles.PROJECT_VALIDATION_MANAGER),
                    permissionsOf(this, group.asPermissionTarget())
                )
            }
        }
    }

    @Test
    fun `Revoking one project role of an account keeps the other`() {
        asAdmin {
            project {
                val account = doCreateAccount()
                accountService.grantProjectPermission(id, PermissionTargetType.ACCOUNT, account.id(), Roles.PROJECT_PARTICIPANT)
                accountService.grantProjectPermission(id, PermissionTargetType.ACCOUNT, account.id(), Roles.PROJECT_VALIDATION_MANAGER)
                accountService.revokeProjectPermission(id, PermissionTargetType.ACCOUNT, account.id(), Roles.PROJECT_VALIDATION_MANAGER)
                assertEquals(
                    listOf(Roles.PROJECT_PARTICIPANT),
                    permissionsOf(this, account.asPermissionTarget())
                )
            }
        }
    }

    @Test
    fun `A group with two project roles has the functions of both on the project and none on another`() {
        withNoGrantViewToAll {
            asAdmin {
                val other = project()
                project {
                    val group = doCreateAccountGroup()
                    accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
                    accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_VALIDATION_MANAGER)
                    val account = doCreateAccount(group)
                    asFixedAccount(account) {
                        // From Participant only
                        assertTrue(securityService.isProjectFunctionGranted(this, ProjectView::class.java))
                        assertTrue(securityService.isProjectFunctionGranted(this, ValidationRunStatusCommentEditOwn::class.java))
                        // From Validation manager only
                        assertTrue(securityService.isProjectFunctionGranted(this, ValidationStampEdit::class.java))
                        assertTrue(securityService.isProjectFunctionGranted(this, ValidationRunCreate::class.java))
                        // From neither
                        assertFalse(securityService.isProjectFunctionGranted(this, PromotionRunCreate::class.java))
                        // None on another project
                        assertFalse(securityService.isProjectFunctionGranted(other, ProjectView::class.java))
                        assertFalse(securityService.isProjectFunctionGranted(other, ValidationStampEdit::class.java))
                    }
                }
            }
        }
    }

    @Test
    fun `An account with two project roles has the functions of both`() {
        withNoGrantViewToAll {
            asAdmin {
                project {
                    val account = doCreateAccount()
                    accountService.grantProjectPermission(id, PermissionTargetType.ACCOUNT, account.id(), Roles.PROJECT_PARTICIPANT)
                    accountService.grantProjectPermission(id, PermissionTargetType.ACCOUNT, account.id(), Roles.PROJECT_PROMOTER)
                    asFixedAccount(account) {
                        assertTrue(securityService.isProjectFunctionGranted(this, ValidationRunStatusCommentEditOwn::class.java))
                        assertTrue(securityService.isProjectFunctionGranted(this, PromotionRunCreate::class.java))
                        assertFalse(securityService.isProjectFunctionGranted(this, ValidationStampEdit::class.java))
                    }
                }
            }
        }
    }

    @Test
    fun `Granting an unknown project role is refused`() {
        asAdmin {
            project {
                val group = doCreateAccountGroup()
                assertFailsWith<RoleNotFoundException> {
                    accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), "UNKNOWN_ROLE")
                }
            }
        }
    }

    @Test
    fun `Revoking an unknown project role is refused`() {
        asAdmin {
            project {
                val group = doCreateAccountGroup()
                assertFailsWith<RoleNotFoundException> {
                    accountService.revokeProjectPermission(id, PermissionTargetType.GROUP, group.id(), "UNKNOWN_ROLE")
                }
            }
        }
    }

    @Test
    fun `Granting a project role on an unknown project is refused`() {
        asAdmin {
            val group = doCreateAccountGroup()
            assertFailsWith<ProjectNotFoundException> {
                accountService.grantProjectPermission(ID.of(Int.MAX_VALUE), PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
            }
        }
    }

    @Test
    fun `Granting a project role to an unknown group or account is refused`() {
        asAdmin {
            project {
                assertFailsWith<AccountGroupNotFoundException> {
                    accountService.grantProjectPermission(id, PermissionTargetType.GROUP, Int.MAX_VALUE, Roles.PROJECT_PARTICIPANT)
                }
                assertFailsWith<AccountNotFoundException> {
                    accountService.grantProjectPermission(id, PermissionTargetType.ACCOUNT, Int.MAX_VALUE, Roles.PROJECT_PARTICIPANT)
                }
            }
        }
    }

    @Test
    fun `Granting and revoking a project role needs the project authorisation management`() {
        asAdmin {
            project {
                val group = doCreateAccountGroup()
                accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
                asUserWithView(this).call {
                    assertFailsWith<AccessDeniedException> {
                        accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_OWNER)
                    }
                    assertFailsWith<AccessDeniedException> {
                        accountService.revokeProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
                    }
                }
                asUser().with(id(), ProjectAuthorisationMgt::class.java).call {
                    accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_OWNER)
                }
                assertEquals(
                    listOf(Roles.PROJECT_OWNER, Roles.PROJECT_PARTICIPANT),
                    permissionsOf(this, group.asPermissionTarget())
                )
            }
        }
    }

    /**
     * The project permissions page (#2164) lists who holds each role on the project for the
     * holders of `ProjectAuthorisationMgt` there, like a project owner, who hold no global
     * account management function.
     */
    @Test
    fun `A project owner reads who holds each role on the project`() {
        asAdmin {
            project {
                val group = doCreateAccountGroup()
                val account = doCreateAccount()
                accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
                accountService.grantProjectPermission(id, PermissionTargetType.ACCOUNT, account.id(), Roles.PROJECT_PARTICIPANT)
                val participant = rolesService.getProjectRole(Roles.PROJECT_PARTICIPANT).get()
                asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                    assertEquals(
                        listOf(group.id()),
                        accountService.findAccountGroupsByProjectRole(this, participant).map { it.id() }
                    )
                    assertEquals(
                        listOf(account.id()),
                        accountService.findAccountsByProjectRole(this, participant).map { it.id() }
                    )
                }
            }
        }
    }

    @Test
    fun `A project owner looks for the accounts and groups to grant a role to on the project`() {
        asAdmin {
            project {
                val prefix = uid("pt-")
                val group = doCreateAccountGroup(name = "$prefix-group")
                val account = doCreateAccount(email = "$prefix-account@example.com")
                asAccountWithProjectRole(Roles.PROJECT_OWNER) {
                    val targets = accountService.searchPermissionTargets(prefix, id)
                    assertTrue(targets.any { it.type == PermissionTargetType.GROUP && it.id == group.id() })
                    assertTrue(targets.any { it.type == PermissionTargetType.ACCOUNT && it.id == account.id() })
                    // ... but not to grant a global role
                    assertFailsWith<AccessDeniedException> {
                        accountService.searchPermissionTargets(prefix)
                    }
                }
                asAccountWithProjectRole(Roles.PROJECT_READ_ONLY) {
                    assertFailsWith<AccessDeniedException> {
                        accountService.searchPermissionTargets(prefix, id)
                    }
                }
            }
        }
    }

    private fun permissionsOf(project: Project, target: PermissionTarget): List<String> =
        accountService.getProjectPermissions(project.id)
            .filter { it.target.type == target.type && it.target.id == target.id }
            .map { it.role.id }
            .sorted()
}
