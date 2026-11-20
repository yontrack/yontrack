package net.nemerosa.ontrack.extension.casc.context.core.admin

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonArrayType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

/**
 * Global permissions for the account groups.
 */
@Component
class AccountGroupGlobalPermissionsAdminContext(
    private val accountService: AccountService,
    private val rolesService: RolesService,
) : AbstractCascContext(), SubAdminContext {

    companion object {
        const val PRIORITY = AccountGroupsAdminContext.PRIORITY - 1
    }

    private val logger: Logger = LoggerFactory.getLogger(AccountGroupGlobalPermissionsAdminContext::class.java)

    override val field: String = "group-permissions"

    override val priority: Int = PRIORITY

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonArrayType(
            items = jsonTypeBuilder.toType(CascAccountGroupPermission::class),
            description = "List of account groups global permissions. A listed group gets exactly the listed roles, the other groups are left untouched.",
        )
    }

    override fun run(node: JsonNode, paths: List<String>) {
        // Items to provision
        val items = node.mapIndexed { index, child ->
            try {
                child.parse<CascAccountGroupPermission>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${CascAccountGroupPermission::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }.mapIndexed { index, input ->
            CascAccountGroupPermissionMapping(
                group = accountService.findAccountGroupByName(input.group)
                    ?: error("Cannot find group [${input.group}] at ${path(paths + index.toString())}"),
                role = rolesService.getGlobalRole(input.role).getOrNull()
                    ?: error("Cannot find role [${input.role}] at ${path(paths + index.toString())}"),
            )
        }
        // For every group listed, its global roles become exactly the listed ones (roles add up,
        // ADR 0023). The groups which are not listed are left untouched.
        items.groupBy { it.group.id() }.values.forEach { groupItems ->
            val group = groupItems.first().group
            val roles = groupItems.map { it.role.id }.toSet()
            val existing = accountService.getGlobalRolesForAccountGroup(group).map { it.id }.toSet()
            (roles - existing).forEach { role ->
                logger.info("Granting global role to account group: ${group.name} --> $role")
                accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), role)
            }
            (existing - roles).forEach { role ->
                logger.info("Revoking global role from account group: ${group.name} --> $role")
                accountService.revokeGlobalPermission(PermissionTargetType.GROUP, group.id(), role)
            }
        }
    }

    override fun render(): JsonNode = accountService.globalPermissions.filter {
        it.target.type == PermissionTargetType.GROUP
    }.map {
        CascAccountGroupPermission(
            group = it.target.name,
            role = it.role.id,
        )
    }.asJson()

    @APIDescription("Association between a group and a role")
    data class CascAccountGroupPermission(
        @APIDescription("Name of the group")
        val group: String,
        @APIDescription("ID of the role")
        val role: String,
    )

    @APIDescription("Association between a group and a role")
    data class CascAccountGroupPermissionMapping(
        val group: AccountGroup,
        val role: GlobalRole,
    )
}