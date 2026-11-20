package net.nemerosa.ontrack.model.security;

import net.nemerosa.ontrack.model.Ack;
import net.nemerosa.ontrack.model.structure.ID;
import net.nemerosa.ontrack.model.structure.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Management of accounts.
 */
public interface AccountService {

    /**
     * List of accounts
     */
    List<Account> getAccounts();

    /**
     * Creates a built-in account
     */
    Account create(AccountInput input);

    /**
     * Gets an account using its ID
     */
    Account getAccount(ID accountId);

    /**
     * Gets the groups for an account
     */
    List<AccountGroup> getGroupsForAccount(ID accountId);

    /**
     * Updating an existing account
     *
     * @param accountId ID of the account to update
     * @param input     Update data
     * @return Updated account
     */
    Account updateAccount(ID accountId, AccountInput input);

    /**
     * Deletes an account
     */
    Ack deleteAccount(ID accountId);

    /**
     * List of account groups
     */
    List<AccountGroup> getAccountGroups();

    /**
     * Creation of an account group
     */
    AccountGroup createGroup(AccountGroupInput input);

    /**
     * Getting a group
     */
    AccountGroup getAccountGroup(ID groupId);

    /**
     * Updating a group
     */
    AccountGroup updateGroup(ID groupId, AccountGroupInput input);

    /**
     * Deleting a group.
     */
    Ack deleteGroup(ID groupId);

    /**
     * Returns the list of <i>all</i> account groups, together with the status for their selection
     * in the given account ID. The account ID may not be {@link net.nemerosa.ontrack.model.structure.ID#isSet() set},
     * meaning that none of the groups will be selected.
     *
     * @param accountId Account ID to get the groups for, or not {@link net.nemerosa.ontrack.model.structure.ID#isSet() set}
     * @return List of groups with their selection status
     */
    List<AccountGroupSelection> getAccountGroupsForSelection(ID accountId);

    /**
     * Searches for a list of permission targets using the <code>token</code> as a part in the
     * name of the permission target.
     */
    Collection<PermissionTarget> searchPermissionTargets(String token);

    /**
     * Searches for the permission targets to grant a role to on a project, using the
     * <code>token</code> as a part in the name of the permission target.
     * <p>
     * Checked against the management of the permissions of this project
     * ({@link ProjectAuthorisationMgt}) instead of the global management of the accounts.
     *
     * @param token     Part of the name of the permission target
     * @param projectId ID of the project to grant a role on
     */
    Collection<PermissionTarget> searchPermissionTargets(String token, ID projectId);

    /**
     * Saves the global permission for the {@link net.nemerosa.ontrack.model.security.PermissionTarget} defined
     * by the <code>type</code> and <code>id</code>, <b>replacing</b> all the global roles the target holds.
     *
     * @param type  Group or account level permission
     * @param id    ID of the group or account
     * @param input Permissions to set
     * @see #grantGlobalPermission(PermissionTargetType, int, String) to add a role instead
     */
    Ack saveGlobalPermission(PermissionTargetType type, int id, PermissionInput input);

    /**
     * Grants a global role to the {@link net.nemerosa.ontrack.model.security.PermissionTarget} defined
     * by the <code>type</code> and <code>id</code>, keeping the other global roles it holds.
     * Does nothing when the target already holds the role.
     *
     * @param type Group or account
     * @param id   ID of the group or account
     * @param role ID of the global role to grant
     * @throws net.nemerosa.ontrack.model.exceptions.RoleNotFoundException when the global role does not exist
     * @throws net.nemerosa.ontrack.model.exceptions.NotFoundException when the account or the group does not exist
     */
    Ack grantGlobalPermission(PermissionTargetType type, int id, String role);

    /**
     * Revokes one global role from the {@link net.nemerosa.ontrack.model.security.PermissionTarget} defined
     * by the <code>type</code> and <code>id</code>, keeping the other global roles it holds.
     *
     * @param type Group or account
     * @param id   ID of the group or account
     * @param role ID of the global role to revoke
     * @throws net.nemerosa.ontrack.model.exceptions.RoleNotFoundException when the global role does not exist
     */
    Ack revokeGlobalPermission(PermissionTargetType type, int id, String role);

    /**
     * Gets the list of global permissions: one entry per target and role, since a target may hold
     * several global roles.
     */
    Collection<GlobalPermission> getGlobalPermissions();

    /**
     * Deletes all the global roles of a target.
     */
    Ack deleteGlobalPermission(PermissionTargetType type, int id);

    /**
     * Gets the list of permissions for a project: one entry per target and role, since a target may
     * hold several roles on a project.
     *
     * @param projectId ID of the project
     */
    Collection<ProjectPermission> getProjectPermissions(ID projectId);

    /**
     * Saves the project permission for the {@link net.nemerosa.ontrack.model.security.PermissionTarget} defined
     * by the <code>type</code> and <code>id</code>, <b>replacing</b> all the roles the target holds on
     * the project.
     *
     * @see #grantProjectPermission(ID, PermissionTargetType, int, String) to add a role instead
     */
    Ack saveProjectPermission(ID projectId, PermissionTargetType type, int id, PermissionInput input);

    /**
     * Grants a project role to the {@link net.nemerosa.ontrack.model.security.PermissionTarget} defined
     * by the <code>type</code> and <code>id</code>, keeping the other roles it holds on the project.
     * Does nothing when the target already holds the role.
     *
     * @param projectId ID of the project
     * @param type      Group or account
     * @param id        ID of the group or account
     * @param role      ID of the project role to grant
     * @throws net.nemerosa.ontrack.model.exceptions.RoleNotFoundException when the project role does not exist
     * @throws net.nemerosa.ontrack.model.exceptions.NotFoundException when the project, the account or the group does not exist
     */
    Ack grantProjectPermission(ID projectId, PermissionTargetType type, int id, String role);

    /**
     * Revokes one project role from the {@link net.nemerosa.ontrack.model.security.PermissionTarget} defined
     * by the <code>type</code> and <code>id</code>, keeping the other roles it holds on the project.
     *
     * @param projectId ID of the project
     * @param type      Group or account
     * @param id        ID of the group or account
     * @param role      ID of the project role to revoke
     * @throws net.nemerosa.ontrack.model.exceptions.RoleNotFoundException when the project role does not exist
     */
    Ack revokeProjectPermission(ID projectId, PermissionTargetType type, int id, String role);

    /**
     * Deletes all the project roles of a target on a project.
     */
    Ack deleteProjectPermission(ID projectId, PermissionTargetType type, int id);

    /**
     * Gets the list of project permissions for a given account
     *
     * @param account Account to get the permissions for
     * @return List of project roles
     */
    Collection<ProjectRoleAssociation> getProjectPermissionsForAccount(Account account);

    /**
     * Gets all the global roles of an account, sorted by ID - not those of its groups.
     *
     * @param account Account
     * @return Global roles of the account, empty if none
     */
    List<GlobalRole> getGlobalRolesForAccount(Account account);

    /**
     * Gets the list of accounts associated with this account group.
     *
     * @param accountGroup Account group
     * @return List of accounts
     */
    List<Account> getAccountsForGroup(AccountGroup accountGroup);

    /**
     * Gets all the global roles of an account group, sorted by ID.
     *
     * @param group Account group
     * @return Global roles of the group, empty if none
     */
    List<GlobalRole> getGlobalRolesForAccountGroup(AccountGroup group);

    /**
     * Gets the list of project permissions for a given account group
     *
     * @param group Account group to get the permissions for
     * @return List of project roles
     */
    Collection<ProjectRoleAssociation> getProjectPermissionsForAccountGroup(AccountGroup group);

    /**
     * List of groups having the given global role
     *
     * @param globalRole Global role to search for
     * @return List of matching account groups
     */
    Collection<AccountGroup> findAccountGroupsByGlobalRole(GlobalRole globalRole);

    /**
     * List of accounts having the given global role
     *
     * @param globalRole Global role to search for
     * @return List of matching accounts
     */
    Collection<Account> findAccountsByGlobalRole(GlobalRole globalRole);

    /**
     * List of groups having the given project role
     *
     * @param project     Project
     * @param projectRole Role to search for
     * @return List of matching account groups
     */
    Collection<AccountGroup> findAccountGroupsByProjectRole(Project project, ProjectRole projectRole);

    /**
     * List of accounts having the given project role
     *
     * @param project     Project
     * @param projectRole Role to search for
     * @return List of matching accounts
     */
    Collection<Account> findAccountsByProjectRole(Project project, ProjectRole projectRole);

    /**
     * Finds an account using its name.
     *
     * @param username Name to look for
     * @return Account or null if not found
     */
    @Nullable
    Account findAccountByName(@NotNull String username);

    /**
     * Finds an account group using its name.
     *
     * @param name Name to look for
     * @return Account group or null if not found
     */
    @Nullable
    AccountGroup findAccountGroupByName(@NotNull String name);

}
