package net.nemerosa.ontrack.extension.casc.context.core.admin

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.syncForward
import net.nemerosa.ontrack.extension.casc.context.AbstractCascContext
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.json.schema.JsonArrayType
import net.nemerosa.ontrack.model.json.schema.JsonType
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.json.schema.toType
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.ID
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
            description = "List of account groups global permissions (old permissions are preserved)",
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
        // Existing items
        val existing = accountService.globalPermissions.filter {
            it.target.type == PermissionTargetType.GROUP
        }.map {
            CascAccountGroupPermissionMapping(
                group = accountService.getAccountGroup(ID.of(it.target.id)),
                role = it.role,
            )
        }
        // Synchronizing, preserving the existing groups
        syncForward(
            from = items,
            to = existing
        ) {
            equality { a, b -> a.group.id == b.group.id }
            onCreation { item ->
                logger.info("Creating account group permission: ${item.group.name} --> ${item.role.id}")
                accountService.saveGlobalPermission(
                    PermissionTargetType.GROUP,
                    item.group.id(),
                    PermissionInput(role = item.role.id),
                )
            }
            onModification { item, _ ->
                logger.info("Updating account group permission: ${item.group.name} --> ${item.role.id}")
                accountService.saveGlobalPermission(
                    PermissionTargetType.GROUP,
                    item.group.id(),
                    PermissionInput(role = item.role.id),
                )
            }
            onDeletion { existing ->
                logger.info("Preserving existing group permission: ${existing.group.name} --> ${existing.role.id}")
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