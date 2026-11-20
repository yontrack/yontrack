package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.exceptions.RoleNotFoundException
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.Entity
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.repository.AccountGroupRepository
import net.nemerosa.ontrack.repository.AccountRepository
import net.nemerosa.ontrack.repository.RoleRepository
import net.nemerosa.ontrack.repository.StructureRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.util.stream.Collectors
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional
class AccountServiceImpl(
    private val roleRepository: RoleRepository,
    private val rolesService: RolesService,
    private val accountRepository: AccountRepository,
    private val accountGroupRepository: AccountGroupRepository,
    private val securityService: SecurityService,
    private val structureRepository: StructureRepository,
) : AccountService {

    override fun getAccounts(): List<Account> {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        return accountRepository.findAll().toList()
    }

    override fun create(input: AccountInput): Account {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        // Agents are registered, never created as accounts
        if (AgentIdentifiers.isAgentIdentifier(input.email)) {
            throw AgentIdentifierRefusedException(input.email)
        }
        // Creates the account
        var account = Account.user(
            fullName = input.fullName,
            email = input.email,
        )
        // Saves it
        account = accountRepository.newAccount(account)
        // Account groups
        accountGroupRepository.linkAccountToGroups(account.id(), input.groups)
        // OK
        return account
    }

    override fun updateAccount(accountId: ID, input: AccountInput): Account {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        // Gets the existing account
        var account = getAccount(accountId)
        // An agent is edited as an agent, and has no groups
        if (account.isAgent) {
            throw AgentAccountEditionException(account.email)
        }
        if (AgentIdentifiers.isAgentIdentifier(input.email)) {
            throw AgentIdentifierRefusedException(input.email)
        }
        // Updates it
        account = account.update(input)
        // Saves it
        accountRepository.saveAccount(account)
        // Account groups
        accountGroupRepository.linkAccountToGroups(account.id(), input.groups)
        // OK
        return getAccount(accountId)
    }

    override fun deleteAccount(accountId: ID): Ack {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        return accountRepository.deleteAccount(accountId)
    }

    override fun getAccountGroups(): List<AccountGroup> {
        securityService.checkGlobalFunction(AccountGroupManagement::class.java)
        return accountGroupRepository.findAll()
    }

    override fun createGroup(input: AccountGroupInput): AccountGroup {
        securityService.checkGlobalFunction(AccountGroupManagement::class.java)
        // Creates the account group
        val group = AccountGroup(ID.NONE, input.name, input.description)
        // Saves it
        return accountGroupRepository.newAccountGroup(group)
    }

    override fun getAccountGroup(groupId: ID): AccountGroup {
        securityService.checkGlobalFunction(AccountGroupManagement::class.java)
        return accountGroupRepository.getById(groupId)
    }

    override fun updateGroup(groupId: ID, input: AccountGroupInput): AccountGroup {
        securityService.checkGlobalFunction(AccountGroupManagement::class.java)
        val group = getAccountGroup(groupId).update(input)
        accountGroupRepository.update(group)
        return group
    }

    override fun deleteGroup(groupId: ID): Ack {
        securityService.checkGlobalFunction(AccountGroupManagement::class.java)
        return accountGroupRepository.delete(groupId)
    }

    override fun getAccountGroupsForSelection(accountId: ID): List<AccountGroupSelection> { // Account groups or none
        val accountGroupIds: Set<Int> = (accountId.ifSet { accountGroupRepository.findByAccount(it) } ?: emptyList())
            .map { obj: Entity -> obj.id() }
            .toSet()
        // Collection of groups with the selection
        return accountGroups
            .map { group: AccountGroup -> AccountGroupSelection(group, accountGroupIds.contains(group.id())) }
    }

    override fun searchPermissionTargets(token: String): Collection<PermissionTarget> {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        return findPermissionTargets(token)
    }

    override fun searchPermissionTargets(token: String, projectId: ID): Collection<PermissionTarget> {
        securityService.checkProjectFunction(projectId.value, ProjectAuthorisationMgt::class.java)
        return findPermissionTargets(token)
    }

    private fun findPermissionTargets(token: String): Collection<PermissionTarget> {
        val targets: MutableList<PermissionTarget> = ArrayList()
        // Users first - agents have no ACL of their own
        targets.addAll(
            accountRepository.findByNameToken(token).filter { !it.isAgent }.map { it.asPermissionTarget() }
        )
        // ... then groups
        targets.addAll(
            accountGroupRepository.findByNameToken(token).map { it.asPermissionTarget() }
        )
        // OK
        return targets
    }

    override fun saveGlobalPermission(type: PermissionTargetType, id: Int, input: PermissionInput): Ack {
        return when (type) {
            PermissionTargetType.ACCOUNT -> {
                securityService.checkGlobalFunction(AccountManagement::class.java)
                // A mistyped role must not wipe the current roles (#2170)
                checkGlobalRole(input.role)
                roleRepository.saveGlobalRoleForAccount(id, input.role)
            }

            PermissionTargetType.GROUP -> {
                securityService.checkGlobalFunction(AccountGroupManagement::class.java)
                checkGlobalRole(input.role)
                roleRepository.saveGlobalRoleForGroup(id, input.role)
            }

            else -> Ack.NOK
        }
    }

    override fun grantGlobalPermission(type: PermissionTargetType, id: Int, role: String): Ack =
        when (type) {
            PermissionTargetType.ACCOUNT -> {
                securityService.checkGlobalFunction(AccountManagement::class.java)
                checkGlobalRole(role)
                // Not found errors rather than constraint violations
                accountRepository.getAccount(ID.of(id))
                roleRepository.grantGlobalRoleForAccount(id, role)
            }

            PermissionTargetType.GROUP -> {
                securityService.checkGlobalFunction(AccountGroupManagement::class.java)
                checkGlobalRole(role)
                accountGroupRepository.getById(ID.of(id))
                roleRepository.grantGlobalRoleForGroup(id, role)
            }
        }

    override fun revokeGlobalPermission(type: PermissionTargetType, id: Int, role: String): Ack =
        when (type) {
            PermissionTargetType.ACCOUNT -> {
                securityService.checkGlobalFunction(AccountManagement::class.java)
                // A mistyped role is an error, not a silent no-op
                checkGlobalRole(role)
                roleRepository.revokeGlobalRoleForAccount(id, role)
            }

            PermissionTargetType.GROUP -> {
                securityService.checkGlobalFunction(AccountGroupManagement::class.java)
                checkGlobalRole(role)
                roleRepository.revokeGlobalRoleForGroup(id, role)
            }
        }

    private fun checkGlobalRole(role: String) {
        rolesService.getGlobalRole(role).getOrNull() ?: throw RoleNotFoundException(role)
    }

    override fun getGlobalPermissions(): Collection<GlobalPermission> {
        // Each part is shown to the holders of the function which grants its roles (#2170)
        val accounts = securityService.isGlobalFunctionGranted(AccountManagement::class.java)
        val groups = securityService.isGlobalFunctionGranted(AccountGroupManagement::class.java)
        if (!accounts && !groups) {
            securityService.checkGlobalFunction(AccountManagement::class.java)
        }
        val permissions: MutableCollection<GlobalPermission> = ArrayList()
        // Users first
        if (accounts) {
            permissions.addAll(
                accountRepository.findAll().flatMap { account ->
                    getGlobalRolesForAccount(account).map { GlobalPermission(account.asPermissionTarget(), it) }
                }
            )
        }
        // ... then groups
        if (groups) {
            permissions.addAll(
                accountGroupRepository.findAll().flatMap { group ->
                    getGlobalRolesForAccountGroup(group).map { GlobalPermission(group.asPermissionTarget(), it) }
                }
            )
        }
        // OK
        return permissions
    }

    override fun deleteGlobalPermission(type: PermissionTargetType, id: Int): Ack {
        return when (type) {
            PermissionTargetType.ACCOUNT -> {
                securityService.checkGlobalFunction(AccountManagement::class.java)
                roleRepository.deleteGlobalRoleForAccount(id)
            }

            PermissionTargetType.GROUP -> {
                securityService.checkGlobalFunction(AccountGroupManagement::class.java)
                roleRepository.deleteGlobalRoleForGroup(id)
            }

            else -> Ack.NOK
        }
    }

    override fun getProjectPermissions(projectId: ID): Collection<ProjectPermission> {
        securityService.checkProjectFunction(projectId.value, ProjectAuthorisationMgt::class.java)
        val permissions: MutableCollection<ProjectPermission> = ArrayList()
        // Users first
        permissions.addAll(
            accountRepository.findAll().flatMap { getProjectPermissions(projectId, it) }
        )
        // ... then groups
        permissions.addAll(
            accountGroupRepository.findAll().flatMap { getGroupProjectPermissions(projectId, it) }
        )
        // OK
        return permissions
    }

    override fun saveProjectPermission(
        projectId: ID,
        type: PermissionTargetType,
        id: Int,
        input: PermissionInput,
    ): Ack {
        securityService.checkProjectFunction(projectId.value, ProjectAuthorisationMgt::class.java)
        // A mistyped role must not wipe the current roles (#2170)
        checkProjectRole(input.role)
        return when (type) {
            PermissionTargetType.ACCOUNT -> roleRepository.saveProjectRoleForAccount(projectId.value, id, input.role)
            PermissionTargetType.GROUP -> roleRepository.saveProjectRoleForGroup(projectId.value, id, input.role)
            else -> Ack.NOK
        }
    }

    override fun grantProjectPermission(projectId: ID, type: PermissionTargetType, id: Int, role: String): Ack {
        securityService.checkProjectFunction(projectId.value, ProjectAuthorisationMgt::class.java)
        checkProjectRole(role)
        // Not found errors rather than constraint violations
        structureRepository.getProject(projectId)
        return when (type) {
            PermissionTargetType.ACCOUNT -> {
                accountRepository.getAccount(ID.of(id))
                roleRepository.grantProjectRoleForAccount(projectId.value, id, role)
            }

            PermissionTargetType.GROUP -> {
                accountGroupRepository.getById(ID.of(id))
                roleRepository.grantProjectRoleForGroup(projectId.value, id, role)
            }
        }
    }

    override fun revokeProjectPermission(projectId: ID, type: PermissionTargetType, id: Int, role: String): Ack {
        securityService.checkProjectFunction(projectId.value, ProjectAuthorisationMgt::class.java)
        // A mistyped role is an error, not a silent no-op
        checkProjectRole(role)
        return when (type) {
            PermissionTargetType.ACCOUNT -> roleRepository.revokeProjectRoleForAccount(projectId.value, id, role)
            PermissionTargetType.GROUP -> roleRepository.revokeProjectRoleForGroup(projectId.value, id, role)
        }
    }

    private fun checkProjectRole(role: String) {
        rolesService.getProjectRole(role).getOrNull() ?: throw RoleNotFoundException(role)
    }

    override fun deleteProjectPermission(projectId: ID, type: PermissionTargetType, id: Int): Ack {
        securityService.checkProjectFunction(projectId.value, ProjectAuthorisationMgt::class.java)
        return when (type) {
            PermissionTargetType.ACCOUNT -> roleRepository.deleteProjectRoleForAccount(projectId.value, id)
            PermissionTargetType.GROUP -> roleRepository.deleteProjectRoleForGroup(projectId.value, id)
            else -> Ack.NOK
        }
    }

    override fun getProjectPermissionsForAccount(account: Account): Collection<ProjectRoleAssociation> {
        return roleRepository.findProjectRoleAssociationsByAccount(
            account.id()
        ) { project: Int?, roleId: String? ->
            rolesService.getProjectRoleAssociation(
                project!!,
                roleId!!
            )
        }
            .stream() // Filter by authorisation
            .filter { projectRoleAssociation: ProjectRoleAssociation ->
                securityService.isProjectFunctionGranted(
                    projectRoleAssociation.projectId,
                    ProjectAuthorisationMgt::class.java
                )
            } // OK
            .collect(Collectors.toList())
    }

    override fun getGlobalRolesForAccount(account: Account): List<GlobalRole> =
        globalRoles(roleRepository.findGlobalRolesByAccount(account.id()))

    override fun getAccountsForGroup(accountGroup: AccountGroup): List<Account> {
        return accountRepository.getAccountsForGroup(accountGroup)
    }

    override fun getGlobalRolesForAccountGroup(group: AccountGroup): List<GlobalRole> =
        globalRoles(roleRepository.findGlobalRolesByGroup(group.id()))

    /**
     * Global roles from their IDs, ignoring the ones which are no longer defined.
     */
    private fun globalRoles(ids: List<String>): List<GlobalRole> =
        ids.mapNotNull { rolesService.getGlobalRole(it).getOrNull() }

    override fun getProjectPermissionsForAccountGroup(group: AccountGroup): Collection<ProjectRoleAssociation> {
        return roleRepository.findProjectRoleAssociationsByGroup(group.id()) { project: Int, roleId: String ->
            rolesService.getProjectRoleAssociation(project, roleId)
        }
            .filter { projectRoleAssociation: ProjectRoleAssociation ->
                securityService.isProjectFunctionGranted(
                    projectRoleAssociation.projectId,
                    ProjectAuthorisationMgt::class.java
                )
            }
    }

    override fun findAccountGroupsByGlobalRole(globalRole: GlobalRole): Collection<AccountGroup> {
        return roleRepository.findAccountGroupsByGlobalRole(globalRole) { groupId: ID -> getAccountGroup(groupId) }
    }

    override fun findAccountsByGlobalRole(globalRole: GlobalRole): Collection<Account> {
        return roleRepository.findAccountsByGlobalRole(globalRole) { accountId: ID -> getAccount(accountId) }
    }

    /**
     * The holders of `ProjectAuthorisationMgt` on the project - a project owner, for example -
     * read who holds its roles without managing the accounts globally: they are the ones
     * granting these roles (#2164). Anybody else still needs the global management functions.
     */
    override fun findAccountGroupsByProjectRole(project: Project, projectRole: ProjectRole): Collection<AccountGroup> {
        val projectAuthorisationMgt = securityService.isProjectFunctionGranted(project, ProjectAuthorisationMgt::class.java)
        return roleRepository.findAccountGroupsByProjectRole(project, projectRole) { groupId: ID ->
            if (projectAuthorisationMgt) {
                accountGroupRepository.getById(groupId)
            } else {
                getAccountGroup(groupId)
            }
        }
    }

    /**
     * See [findAccountGroupsByProjectRole] for the security.
     */
    override fun findAccountsByProjectRole(project: Project, projectRole: ProjectRole): Collection<Account> {
        val projectAuthorisationMgt = securityService.isProjectFunctionGranted(project, ProjectAuthorisationMgt::class.java)
        return roleRepository.findAccountsByProjectRole(project, projectRole) { accountId: ID ->
            if (projectAuthorisationMgt) {
                accountRepository.getAccount(accountId)
            } else {
                getAccount(accountId)
            }
        }
    }

    private fun getGroupProjectPermissions(projectId: ID, accountGroup: AccountGroup): List<ProjectPermission> =
        roleRepository.findProjectRoleAssociationsByGroup(
            accountGroup.id(),
            projectId.value
        ) { project: Int, roleId: String -> rolesService.getProjectRoleAssociation(project, roleId) }
            .map { ProjectPermission(projectId, accountGroup.asPermissionTarget(), it.projectRole) }

    private fun getProjectPermissions(projectId: ID, account: Account): List<ProjectPermission> =
        roleRepository.findProjectRoleAssociationsByAccount(
            account.id(),
            projectId.value
        ) { project: Int, roleId: String -> rolesService.getProjectRoleAssociation(project, roleId) }
            .map { ProjectPermission(projectId, account.asPermissionTarget(), it.projectRole) }

    override fun getAccount(accountId: ID): Account {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        return accountRepository.getAccount(accountId)
    }

    override fun getGroupsForAccount(accountId: ID): List<AccountGroup> {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        return accountGroupRepository.findByAccount(accountId.value).toList()
    }

    override fun findAccountByName(username: String): Account? {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        return accountRepository.findAccountByName(username)
    }

    override fun findAccountGroupByName(name: String): AccountGroup? {
        securityService.checkGlobalFunction(AccountManagement::class.java)
        return accountGroupRepository.findAccountGroupByName(name)
    }

}