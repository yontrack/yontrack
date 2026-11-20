package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AccountGroup
import net.nemerosa.ontrack.model.security.ProjectRole
import net.nemerosa.ontrack.model.security.ProjectRoleAssociation
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.security.SecurityRole
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Project roles add up (ADR 0023): granting inserts a role, revoking deletes one role.
 */
class RoleJdbcRepositoryIT : AbstractRepositoryTestSupport() {

    @Autowired
    private lateinit var roleRepository: RoleRepository

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var accountGroupRepository: AccountGroupRepository

    @Test
    fun `Granting two project roles to an account keeps both`() {
        val project = do_create_project()
        val account = newAccount()
        roleRepository.grantProjectRoleForAccount(project.id(), account.id(), Roles.PROJECT_PARTICIPANT)
        roleRepository.grantProjectRoleForAccount(project.id(), account.id(), Roles.PROJECT_VALIDATION_MANAGER)
        assertEquals(
            setOf(Roles.PROJECT_PARTICIPANT, Roles.PROJECT_VALIDATION_MANAGER),
            accountRoles(account, project.id()),
        )
    }

    @Test
    fun `Granting two project roles to a group keeps both`() {
        val project = do_create_project()
        val group = newGroup()
        roleRepository.grantProjectRoleForGroup(project.id(), group.id(), Roles.PROJECT_PARTICIPANT)
        roleRepository.grantProjectRoleForGroup(project.id(), group.id(), Roles.PROJECT_VALIDATION_MANAGER)
        assertEquals(
            setOf(Roles.PROJECT_PARTICIPANT, Roles.PROJECT_VALIDATION_MANAGER),
            groupRoles(group, project.id()),
        )
    }

    @Test
    fun `Granting a project role twice to an account is a no-op`() {
        val project = do_create_project()
        val account = newAccount()
        roleRepository.grantProjectRoleForAccount(project.id(), account.id(), Roles.PROJECT_PARTICIPANT)
        roleRepository.grantProjectRoleForAccount(project.id(), account.id(), Roles.PROJECT_PARTICIPANT)
        assertEquals(
            listOf(Roles.PROJECT_PARTICIPANT),
            roleRepository.findProjectRoleAssociationsByAccount(account.id(), project.id(), mapper).map { it.projectRole.id },
        )
    }

    @Test
    fun `Granting a project role twice to a group is a no-op`() {
        val project = do_create_project()
        val group = newGroup()
        roleRepository.grantProjectRoleForGroup(project.id(), group.id(), Roles.PROJECT_PARTICIPANT)
        roleRepository.grantProjectRoleForGroup(project.id(), group.id(), Roles.PROJECT_PARTICIPANT)
        assertEquals(
            listOf(Roles.PROJECT_PARTICIPANT),
            roleRepository.findProjectRoleAssociationsByGroup(group.id(), project.id(), mapper).map { it.projectRole.id },
        )
    }

    @Test
    fun `Revoking one project role of an account keeps the other`() {
        val project = do_create_project()
        val account = newAccount()
        roleRepository.grantProjectRoleForAccount(project.id(), account.id(), Roles.PROJECT_PARTICIPANT)
        roleRepository.grantProjectRoleForAccount(project.id(), account.id(), Roles.PROJECT_VALIDATION_MANAGER)
        assertTrue(
            roleRepository.revokeProjectRoleForAccount(project.id(), account.id(), Roles.PROJECT_PARTICIPANT).success
        )
        assertEquals(setOf(Roles.PROJECT_VALIDATION_MANAGER), accountRoles(account, project.id()))
    }

    @Test
    fun `Revoking one project role of a group keeps the other`() {
        val project = do_create_project()
        val group = newGroup()
        roleRepository.grantProjectRoleForGroup(project.id(), group.id(), Roles.PROJECT_PARTICIPANT)
        roleRepository.grantProjectRoleForGroup(project.id(), group.id(), Roles.PROJECT_VALIDATION_MANAGER)
        assertTrue(
            roleRepository.revokeProjectRoleForGroup(project.id(), group.id(), Roles.PROJECT_PARTICIPANT).success
        )
        assertEquals(setOf(Roles.PROJECT_VALIDATION_MANAGER), groupRoles(group, project.id()))
    }

    @Test
    fun `Revoking a project role an account does not hold changes nothing`() {
        val project = do_create_project()
        val account = newAccount()
        roleRepository.grantProjectRoleForAccount(project.id(), account.id(), Roles.PROJECT_PARTICIPANT)
        assertFalse(
            roleRepository.revokeProjectRoleForAccount(project.id(), account.id(), Roles.PROJECT_OWNER).success
        )
        assertEquals(setOf(Roles.PROJECT_PARTICIPANT), accountRoles(account, project.id()))
    }

    @Test
    fun `Granting a project role on one project leaves the other projects untouched`() {
        val project = do_create_project()
        val other = do_create_project()
        val group = newGroup()
        roleRepository.grantProjectRoleForGroup(project.id(), group.id(), Roles.PROJECT_PARTICIPANT)
        roleRepository.grantProjectRoleForGroup(other.id(), group.id(), Roles.PROJECT_OWNER)
        roleRepository.revokeProjectRoleForGroup(other.id(), group.id(), Roles.PROJECT_OWNER)
        assertEquals(setOf(Roles.PROJECT_PARTICIPANT), groupRoles(group, project.id()))
        assertEquals(emptySet(), groupRoles(group, other.id()))
    }

    private val mapper = { projectId: Int, roleId: String ->
        Optional.of(ProjectRoleAssociation(projectId, ProjectRole(roleId, roleId, roleId, emptySet())))
    }

    private fun accountRoles(account: Account, projectId: Int): Set<String> =
        roleRepository.findProjectRoleAssociationsByAccount(account.id(), projectId, mapper)
            .map { it.projectRole.id }
            .toSet()

    private fun groupRoles(group: AccountGroup, projectId: Int): Set<String> =
        roleRepository.findProjectRoleAssociationsByGroup(group.id(), projectId, mapper)
            .map { it.projectRole.id }
            .toSet()

    private fun newAccount(): Account {
        val name = uid("user-")
        return accountRepository.newAccount(
            Account(
                id = ID.NONE,
                fullName = name,
                email = "$name@test.com",
                role = SecurityRole.USER,
            )
        )
    }

    private fun newGroup(): AccountGroup =
        accountGroupRepository.newAccountGroup(AccountGroup(ID.NONE, uid("G"), null))
}
