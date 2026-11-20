package net.nemerosa.ontrack.boot.ui;

import net.nemerosa.ontrack.model.Ack;
import net.nemerosa.ontrack.model.deprecation.DeprecationService;
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface;
import net.nemerosa.ontrack.model.security.*;
import net.nemerosa.ontrack.model.structure.ID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Management of the global and project permissions.
 * <p>
 * Every endpoint is deprecated (ADR 0023): only GraphQL handles the several roles an account or a
 * group may hold. The endpoints keep their former semantics: saving a permission replaces the
 * roles of the target, deleting it removes all of them.
 */
@RestController
@RequestMapping("/rest/accounts/permissions")
public class PermissionController {

    private final AccountService accountService;
    private final RolesService rolesService;
    private final DeprecationService deprecationService;

    @Autowired
    public PermissionController(AccountService accountService, RolesService rolesService, DeprecationService deprecationService) {
        this.accountService = accountService;
        this.rolesService = rolesService;
        this.deprecationService = deprecationService;
    }

    /**
     * Looking for a permission target
     *
     * @deprecated Removed in V7. Use the permissionTargets GraphQL query instead. See #2165
     */
    @Deprecated
    @RequestMapping(value = "search/{token:.*}", method = RequestMethod.GET)
    public List<PermissionTarget> searchPermissionTargets(@PathVariable String token) {
        deprecationService.deprecatedUsage(
                DeprecationSurface.REST,
                "GET /rest/accounts/permissions/search/{token}",
                "Removed in V7. Use the permissionTargets GraphQL query instead. See #2165"
        );
        return accountService.searchPermissionTargets(token).stream().toList();
    }

    /**
     * List of global permissions
     *
     * @deprecated Removed in V7. Use the globalPermissions GraphQL query instead. See #2165
     */
    @Deprecated
    @RequestMapping(value = "globals", method = RequestMethod.GET)
    public List<GlobalPermission> getGlobalPermissions() {
        deprecationService.deprecatedUsage(
                DeprecationSurface.REST,
                "GET /rest/accounts/permissions/globals",
                "Removed in V7. Use the globalPermissions GraphQL query instead. See #2165"
        );
        return accountService.getGlobalPermissions().stream().toList();
    }

    /**
     * List of global roles
     *
     * @deprecated Removed in V7. Use the globalRoles GraphQL query instead. See #2165
     */
    @Deprecated
    @RequestMapping(value = "globals/roles", method = RequestMethod.GET)
    public List<GlobalRole> getGlobalRoles() {
        deprecationService.deprecatedUsage(
                DeprecationSurface.REST,
                "GET /rest/accounts/permissions/globals/roles",
                "Removed in V7. Use the globalRoles GraphQL query instead. See #2165"
        );
        return rolesService.getGlobalRoles();
    }

    /**
     * Saving a global permission, replacing the global roles of the target
     *
     * @deprecated Removed in V7. Use the grantGlobalRoleToAccount and grantGlobalRoleToAccountGroup GraphQL mutations instead. See #2165
     */
    @Deprecated
    @RequestMapping(value = "globals/{type}/{id}", method = RequestMethod.PUT)
    public Ack saveGlobalPermission(@PathVariable PermissionTargetType type, @PathVariable int id, @RequestBody PermissionInput input) {
        deprecationService.deprecatedUsage(
                DeprecationSurface.REST,
                "PUT /rest/accounts/permissions/globals/{type}/{id}",
                "Removed in V7. Use the grantGlobalRoleToAccount and grantGlobalRoleToAccountGroup GraphQL mutations instead. See #2165"
        );
        return accountService.saveGlobalPermission(type, id, input);
    }

    /**
     * Deleting a global permission, removing all the global roles of the target
     *
     * @deprecated Removed in V7. Use the revokeGlobalRoleFromAccount and revokeGlobalRoleFromAccountGroup GraphQL mutations instead. See #2165
     */
    @Deprecated
    @RequestMapping(value = "globals/{type}/{id}", method = RequestMethod.DELETE)
    public Ack deleteGlobalPermission(@PathVariable PermissionTargetType type, @PathVariable int id) {
        deprecationService.deprecatedUsage(
                DeprecationSurface.REST,
                "DELETE /rest/accounts/permissions/globals/{type}/{id}",
                "Removed in V7. Use the revokeGlobalRoleFromAccount and revokeGlobalRoleFromAccountGroup GraphQL mutations instead. See #2165"
        );
        return accountService.deleteGlobalPermission(type, id);
    }

    /**
     * List of project roles
     *
     * @deprecated Removed in V7. Use the Project.projectRoles GraphQL field instead. See #2165
     */
    @Deprecated
    @RequestMapping(value = "projects/roles", method = RequestMethod.GET)
    public List<ProjectRole> getProjectRoles() {
        deprecationService.deprecatedUsage(
                DeprecationSurface.REST,
                "GET /rest/accounts/permissions/projects/roles",
                "Removed in V7. Use the Project.projectRoles GraphQL field instead. See #2165"
        );
        return rolesService.getProjectRoles();
    }

    /**
     * List of permissions for a project.
     *
     * @deprecated Removed in V7. Use the Project.projectRoles GraphQL field instead. See #2165
     */
    @Deprecated
    @RequestMapping(value = "projects/{projectId}", method = RequestMethod.GET)
    public List<ProjectPermission> getProjectPermissions(@PathVariable ID projectId) {
        deprecationService.deprecatedUsage(
                DeprecationSurface.REST,
                "GET /rest/accounts/permissions/projects/{projectId}",
                "Removed in V7. Use the Project.projectRoles GraphQL field instead. See #2165"
        );
        return accountService.getProjectPermissions(projectId).stream().toList();
    }

    /**
     * Saving a project permission, replacing the roles of the target on the project
     *
     * @deprecated Removed in V7. Use the grantProjectRoleToAccount and grantProjectRoleToAccountGroup GraphQL mutations instead. See #2165
     */
    @Deprecated
    @RequestMapping(value = "projects/{projectId}/{type}/{id}", method = RequestMethod.PUT)
    public Ack saveProjectPermission(@PathVariable ID projectId, @PathVariable PermissionTargetType type, @PathVariable int id, @RequestBody PermissionInput input) {
        deprecationService.deprecatedUsage(
                DeprecationSurface.REST,
                "PUT /rest/accounts/permissions/projects/{projectId}/{type}/{id}",
                "Removed in V7. Use the grantProjectRoleToAccount and grantProjectRoleToAccountGroup GraphQL mutations instead. See #2165"
        );
        return accountService.saveProjectPermission(projectId, type, id, input);
    }

    /**
     * Deleting a project permission, removing all the roles of the target on the project
     *
     * @deprecated Removed in V7. Use the revokeProjectRoleFromAccount and revokeProjectRoleFromAccountGroup GraphQL mutations instead. See #2165
     */
    @Deprecated
    @RequestMapping(value = "projects/{projectId}/{type}/{id}", method = RequestMethod.DELETE)
    public Ack deleteProjectPermission(@PathVariable ID projectId, @PathVariable PermissionTargetType type, @PathVariable int id) {
        deprecationService.deprecatedUsage(
                DeprecationSurface.REST,
                "DELETE /rest/accounts/permissions/projects/{projectId}/{type}/{id}",
                "Removed in V7. Use the revokeProjectRoleFromAccount and revokeProjectRoleFromAccountGroup GraphQL mutations instead. See #2165"
        );
        return accountService.deleteProjectPermission(projectId, type, id);
    }

}
