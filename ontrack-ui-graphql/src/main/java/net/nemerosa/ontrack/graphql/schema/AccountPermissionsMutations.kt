package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import net.nemerosa.ontrack.model.security.AccountService
import net.nemerosa.ontrack.model.security.PermissionTargetType
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.structure.ID
import org.springframework.stereotype.Component

@Component
class AccountPermissionsMutations(
    private val accountService: AccountService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation> = listOf(
        unitMutation(
            name = "grantGlobalRoleToAccount",
            description = "Grants a global role to an account, keeping the other global roles it holds. Does nothing when the account already holds the role.",
            input = GrantGlobalRoleToAccountInput::class,
        ) { input ->
            accountService.grantGlobalPermission(
                PermissionTargetType.ACCOUNT,
                input.accountId,
                input.globalRole,
            )
        },
        unitMutation(
            name = "grantGlobalRoleToAccountGroup",
            description = "Grants a global role to an account group, keeping the other global roles it holds. Does nothing when the group already holds the role.",
            input = GrantGlobalRoleToAccountGroupInput::class,
        ) { input ->
            accountService.grantGlobalPermission(
                PermissionTargetType.GROUP,
                input.accountGroupId,
                input.globalRole,
            )
        },
        unitMutation(
            name = "revokeGlobalRoleFromAccount",
            description = "Revokes one global role from an account, keeping the other global roles it holds",
            input = RevokeGlobalRoleFromAccountInput::class,
        ) { input ->
            accountService.revokeGlobalPermission(
                PermissionTargetType.ACCOUNT,
                input.accountId,
                input.globalRole,
            )
        },
        unitMutation(
            name = "revokeGlobalRoleFromAccountGroup",
            description = "Revokes one global role from an account group, keeping the other global roles it holds",
            input = RevokeGlobalRoleFromAccountGroupInput::class,
        ) { input ->
            accountService.revokeGlobalPermission(
                PermissionTargetType.GROUP,
                input.accountGroupId,
                input.globalRole,
            )
        },
        unitMutation(
            name = "deleteGlobalRoleFromAccount",
            description = "Removes all the global roles of an account",
            input = DeleteGlobalRoleFromAccountInput::class,
            deprecation = "Removed in V7. Use revokeGlobalRoleFromAccount instead. See #2162",
        ) { input ->
            accountService.deleteGlobalPermission(
                PermissionTargetType.ACCOUNT,
                input.accountId,
            )
        },
        unitMutation(
            name = "deleteGlobalRoleFromAccountGroup",
            description = "Removes all the global roles of an account group",
            input = DeleteGlobalRoleFromAccountGroupInput::class,
            deprecation = "Removed in V7. Use revokeGlobalRoleFromAccountGroup instead. See #2162",
        ) { input ->
            accountService.deleteGlobalPermission(
                PermissionTargetType.GROUP,
                input.accountGroupId,
            )
        },
        unitMutation(
            name = "grantProjectRoleToAccount",
            description = "Grants a project role to an account, keeping the other roles it holds on the project. Does nothing when the account already holds the role.",
            input = GrantProjectRoleToAccountInput::class,
        ) { input ->
            accountService.grantProjectPermission(
                ID.of(input.projectId),
                PermissionTargetType.ACCOUNT,
                input.accountId,
                input.projectRole,
            )
        },
        unitMutation(
            name = "grantProjectRoleToAccountGroup",
            description = "Grants a project role to an account group, keeping the other roles it holds on the project. Does nothing when the group already holds the role.",
            input = GrantProjectRoleToAccountGroupInput::class,
        ) { input ->
            accountService.grantProjectPermission(
                ID.of(input.projectId),
                PermissionTargetType.GROUP,
                input.accountGroupId,
                input.projectRole,
            )
        },
        unitMutation(
            name = "revokeProjectRoleFromAccount",
            description = "Revokes one project role from an account, keeping the other roles it holds on the project",
            input = RevokeProjectRoleFromAccountInput::class,
        ) { input ->
            accountService.revokeProjectPermission(
                ID.of(input.projectId),
                PermissionTargetType.ACCOUNT,
                input.accountId,
                input.projectRole,
            )
        },
        unitMutation(
            name = "revokeProjectRoleFromAccountGroup",
            description = "Revokes one project role from an account group, keeping the other roles it holds on the project",
            input = RevokeProjectRoleFromAccountGroupInput::class,
        ) { input ->
            accountService.revokeProjectPermission(
                ID.of(input.projectId),
                PermissionTargetType.GROUP,
                input.accountGroupId,
                input.projectRole,
            )
        },
    )
}

data class GrantGlobalRoleToAccountInput(
    @APIDescription("ID of the account")
    val accountId: Int,
    @APIDescription("ID of the global role to grant")
    val globalRole: String,
)

data class GrantGlobalRoleToAccountGroupInput(
    @APIDescription("ID of the account group")
    val accountGroupId: Int,
    @APIDescription("ID of the global role to grant")
    val globalRole: String,
)

data class RevokeGlobalRoleFromAccountInput(
    @APIDescription("ID of the account")
    val accountId: Int,
    @APIDescription("ID of the global role to revoke")
    val globalRole: String,
)

data class RevokeGlobalRoleFromAccountGroupInput(
    @APIDescription("ID of the account group")
    val accountGroupId: Int,
    @APIDescription("ID of the global role to revoke")
    val globalRole: String,
)

data class DeleteGlobalRoleFromAccountInput(
    val accountId: Int,
)

data class DeleteGlobalRoleFromAccountGroupInput(
    val accountGroupId: Int,
)

data class GrantProjectRoleToAccountInput(
    @APIDescription("ID of the project")
    val projectId: Int,
    @APIDescription("ID of the account")
    val accountId: Int,
    @APIDescription("ID of the project role to grant")
    val projectRole: String,
)

data class GrantProjectRoleToAccountGroupInput(
    @APIDescription("ID of the project")
    val projectId: Int,
    @APIDescription("ID of the account group")
    val accountGroupId: Int,
    @APIDescription("ID of the project role to grant")
    val projectRole: String,
)

data class RevokeProjectRoleFromAccountInput(
    @APIDescription("ID of the project")
    val projectId: Int,
    @APIDescription("ID of the account")
    val accountId: Int,
    @APIDescription("ID of the project role to revoke")
    val projectRole: String,
)

data class RevokeProjectRoleFromAccountGroupInput(
    @APIDescription("ID of the project")
    val projectId: Int,
    @APIDescription("ID of the account group")
    val accountGroupId: Int,
    @APIDescription("ID of the project role to revoke")
    val projectRole: String,
)
