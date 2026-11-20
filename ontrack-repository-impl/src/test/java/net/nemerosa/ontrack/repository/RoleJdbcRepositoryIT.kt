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
 * Roles add up (ADR 0023): granting inserts a role, revoking deletes one role - globally and on
 * a project.
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

    @Test
    fun `Granting two global roles to an account keeps both`() {
        val account = newAccount()
        roleRepository.grantGlobalRoleForAccount(account.id(), Roles.GLOBAL_PARTICIPANT)
        roleRepository.grantGlobalRoleForAccount(account.id(), Roles.GLOBAL_CREATOR)
        assertEquals(
            listOf(Roles.GLOBAL_CREATOR, Roles.GLOBAL_PARTICIPANT),
            roleRepository.findGlobalRolesByAccount(account.id()),
        )
    }

    @Test
    fun `Granting two global roles to a group keeps both`() {
        val group = newGroup()
        roleRepository.grantGlobalRoleForGroup(group.id(), Roles.GLOBAL_PARTICIPANT)
        roleRepository.grantGlobalRoleForGroup(group.id(), Roles.GLOBAL_CREATOR)
        assertEquals(
            listOf(Roles.GLOBAL_CREATOR, Roles.GLOBAL_PARTICIPANT),
            roleRepository.findGlobalRolesByGroup(group.id()),
        )
    }

    @Test
    fun `Granting a global role twice is a no-op`() {
        val account = newAccount()
        val group = newGroup()
        repeat(2) {
            roleRepository.grantGlobalRoleForAccount(account.id(), Roles.GLOBAL_PARTICIPANT)
            roleRepository.grantGlobalRoleForGroup(group.id(), Roles.GLOBAL_PARTICIPANT)
        }
        assertEquals(listOf(Roles.GLOBAL_PARTICIPANT), roleRepository.findGlobalRolesByAccount(account.id()))
        assertEquals(listOf(Roles.GLOBAL_PARTICIPANT), roleRepository.findGlobalRolesByGroup(group.id()))
    }

    @Test
    fun `Revoking one global role of an account keeps the other`() {
        val account = newAccount()
        roleRepository.grantGlobalRoleForAccount(account.id(), Roles.GLOBAL_PARTICIPANT)
        roleRepository.grantGlobalRoleForAccount(account.id(), Roles.GLOBAL_CREATOR)
        assertTrue(roleRepository.revokeGlobalRoleForAccount(account.id(), Roles.GLOBAL_PARTICIPANT).success)
        assertEquals(listOf(Roles.GLOBAL_CREATOR), roleRepository.findGlobalRolesByAccount(account.id()))
    }

    @Test
    fun `Revoking one global role of a group keeps the other`() {
        val group = newGroup()
        roleRepository.grantGlobalRoleForGroup(group.id(), Roles.GLOBAL_PARTICIPANT)
        roleRepository.grantGlobalRoleForGroup(group.id(), Roles.GLOBAL_CREATOR)
        assertTrue(roleRepository.revokeGlobalRoleForGroup(group.id(), Roles.GLOBAL_CREATOR).success)
        assertEquals(listOf(Roles.GLOBAL_PARTICIPANT), roleRepository.findGlobalRolesByGroup(group.id()))
    }

    @Test
    fun `Revoking a global role an account does not hold changes nothing`() {
        val account = newAccount()
        roleRepository.grantGlobalRoleForAccount(account.id(), Roles.GLOBAL_PARTICIPANT)
        assertFalse(roleRepository.revokeGlobalRoleForAccount(account.id(), Roles.GLOBAL_ADMINISTRATOR).success)
        assertEquals(listOf(Roles.GLOBAL_PARTICIPANT), roleRepository.findGlobalRolesByAccount(account.id()))
    }

    @Test
    fun `Saving a global role replaces all the others`() {
        val group = newGroup()
        roleRepository.grantGlobalRoleForGroup(group.id(), Roles.GLOBAL_PARTICIPANT)
        roleRepository.grantGlobalRoleForGroup(group.id(), Roles.GLOBAL_CREATOR)
        roleRepository.saveGlobalRoleForGroup(group.id(), Roles.GLOBAL_READ_ONLY)
        assertEquals(listOf(Roles.GLOBAL_READ_ONLY), roleRepository.findGlobalRolesByGroup(group.id()))
    }

    @Test
    fun `Deleting all the global roles of a target holding several succeeds`() {
        val account = newAccount()
        val group = newGroup()
        listOf(Roles.GLOBAL_PARTICIPANT, Roles.GLOBAL_CREATOR).forEach {
            roleRepository.grantGlobalRoleForAccount(account.id(), it)
            roleRepository.grantGlobalRoleForGroup(group.id(), it)
        }
        assertTrue(roleRepository.deleteGlobalRoleForAccount(account.id()).success)
        assertTrue(roleRepository.deleteGlobalRoleForGroup(group.id()).success)
        assertEquals(emptyList(), roleRepository.findGlobalRolesByAccount(account.id()))
        assertEquals(emptyList(), roleRepository.findGlobalRolesByGroup(group.id()))
        // Nothing left to delete
        assertFalse(roleRepository.deleteGlobalRoleForAccount(account.id()).success)
    }

    @Test
    fun `Deleting all the project roles of a target holding several succeeds`() {
        val project = do_create_project()
        val account = newAccount()
        val group = newGroup()
        listOf(Roles.PROJECT_PARTICIPANT, Roles.PROJECT_VALIDATION_MANAGER).forEach {
            roleRepository.grantProjectRoleForAccount(project.id(), account.id(), it)
            roleRepository.grantProjectRoleForGroup(project.id(), group.id(), it)
        }
        assertTrue(roleRepository.deleteProjectRoleForAccount(project.id(), account.id()).success)
        assertTrue(roleRepository.deleteProjectRoleForGroup(project.id(), group.id()).success)
        assertEquals(emptySet(), accountRoles(account, project.id()))
        assertEquals(emptySet(), groupRoles(group, project.id()))
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
