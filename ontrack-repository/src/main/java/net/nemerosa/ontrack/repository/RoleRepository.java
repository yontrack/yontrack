package net.nemerosa.ontrack.repository;

import net.nemerosa.ontrack.model.Ack;
import net.nemerosa.ontrack.model.security.*;
import net.nemerosa.ontrack.model.structure.ID;
import net.nemerosa.ontrack.model.structure.Project;

import java.util.Collection;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface RoleRepository {

    /**
     * Gets the global role for an account
     */
    Optional<String> findGlobalRoleByAccount(int accountId);

    Collection<ProjectRoleAssociation> findProjectRoleAssociationsByAccount(
            int accountId,
            BiFunction<Integer, String, Optional<ProjectRoleAssociation>> projectRoleAssociationMapper
    );

    /**
     * Gets all the roles of an account on a project - an account may hold several roles on a project.
     */
    Collection<ProjectRoleAssociation> findProjectRoleAssociationsByAccount(
            int accountId, int projectId,
            BiFunction<Integer, String, Optional<ProjectRoleAssociation>> projectRoleAssociationMapper);

    Ack saveGlobalRoleForAccount(int accountId, String role);

    Ack saveGlobalRoleForGroup(int accountGroupId, String role);

    /**
     * Gets the global role for an account group
     */
    Optional<String> findGlobalRoleByGroup(int groupId);

    Collection<ProjectRoleAssociation> findProjectRoleAssociationsByGroup(
            int groupId,
            BiFunction<Integer, String, Optional<ProjectRoleAssociation>> projectRoleAssociationMapper
    );

    /**
     * Gets all the roles of a group on a project - a group may hold several roles on a project.
     */
    Collection<ProjectRoleAssociation> findProjectRoleAssociationsByGroup(
            int groupId, int projectId,
            BiFunction<Integer, String, Optional<ProjectRoleAssociation>> projectRoleAssociationMapper
    );

    Ack deleteGlobalRoleForAccount(int accountId);

    Ack deleteGlobalRoleForGroup(int groupId);

    /**
     * Replaces all the roles of the account on the project by the given one.
     * <p>
     * Kept for the replace semantics of {@link net.nemerosa.ontrack.model.security.AccountService#saveProjectPermission}
     * until its callers move to {@link #grantProjectRoleForAccount(int, int, String)}.
     */
    Ack saveProjectRoleForAccount(int projectId, int accountId, String role);

    /**
     * Replaces all the roles of the group on the project by the given one.
     * <p>
     * Kept for the replace semantics of {@link net.nemerosa.ontrack.model.security.AccountService#saveProjectPermission}
     * until its callers move to {@link #grantProjectRoleForGroup(int, int, String)}.
     */
    Ack saveProjectRoleForGroup(int projectId, int accountGroupId, String role);

    /**
     * Grants a role to an account on a project, keeping the other roles of the account on this project.
     * Does nothing when the account already holds the role.
     */
    Ack grantProjectRoleForAccount(int projectId, int accountId, String role);

    /**
     * Grants a role to a group on a project, keeping the other roles of the group on this project.
     * Does nothing when the group already holds the role.
     */
    Ack grantProjectRoleForGroup(int projectId, int accountGroupId, String role);

    /**
     * Revokes one role of an account on a project, keeping its other roles.
     *
     * @return OK if the role was held and is now removed
     */
    Ack revokeProjectRoleForAccount(int projectId, int accountId, String role);

    /**
     * Revokes one role of a group on a project, keeping its other roles.
     *
     * @return OK if the role was held and is now removed
     */
    Ack revokeProjectRoleForGroup(int projectId, int accountGroupId, String role);

    /**
     * Removes all the roles of the account on the project.
     */
    Ack deleteProjectRoleForAccount(int projectId, int accountId);

    /**
     * Removes all the roles of the group on the project.
     */
    Ack deleteProjectRoleForGroup(int projectId, int accountGroupId);

    /**
     * List of groups having the given global role
     *
     * @param globalRole Global role to search for
     * @return List of matching account groups
     */
    Collection<AccountGroup> findAccountGroupsByGlobalRole(GlobalRole globalRole, Function<ID, AccountGroup> accountGroupLoader);

    /**
     * List of accounts having the given global role
     *
     * @param globalRole Global role to search for
     * @return List of matching accounts
     */
    Collection<Account> findAccountsByGlobalRole(GlobalRole globalRole, Function<ID, Account> accountLoader);

    /**
     * List of groups having the given project role
     *
     * @param project     Project
     * @param projectRole Role to search for
     * @return List of matching account groups
     */
    Collection<AccountGroup> findAccountGroupsByProjectRole(Project project, ProjectRole projectRole, Function<ID, AccountGroup> accountGroupLoader);

    /**
     * List of accounts having the given project role
     *
     * @param project     Project
     * @param projectRole Role to search for
     * @return List of matching accounts
     */
    Collection<Account> findAccountsByProjectRole(Project project, ProjectRole projectRole, Function<ID, Account> accountLoader);
}
