package net.nemerosa.ontrack.extension.casc.context.core.admin

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.security.AccountGroupInput
import net.nemerosa.ontrack.model.security.PermissionInput
import net.nemerosa.ontrack.model.security.PermissionTargetType
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

internal class AccountGroupGlobalPermissionsAdminContextIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var accountGroupGlobalPermissionsAdminContext: AccountGroupGlobalPermissionsAdminContext

    @Autowired
    private lateinit var jsonTypeBuilder: JsonTypeBuilder

    @Test
    fun `CasC schema type`() {
        val type = accountGroupGlobalPermissionsAdminContext.jsonType(jsonTypeBuilder)
        assertEquals(
            """
                {
                  "items": {
                    "title": "CascAccountGroupPermission",
                    "properties": {
                      "group": {
                        "description": "Name of the group",
                        "type": "string"
                      },
                      "role": {
                        "description": "ID of the role",
                        "type": "string"
                      }
                    },
                    "required": [
                      "group",
                      "role"
                    ],
                    "additionalProperties": false,
                    "type": "object"
                  },
                  "description": "List of account groups global permissions (old permissions are preserved)",
                  "type": "array"
                }
            """.trimIndent().parseAsJson(),
            type.asJson()
        )
    }

    @Test
    fun `Adding new group roles`() {
        val name = uid("g")
        asAdmin {
            val group = accountService.createGroup(AccountGroupInput(name, "Group $name"))
            casc("""
                ontrack:
                    admin:
                        group-permissions:
                            - group: $name
                              role: AUTOMATION
            """.trimIndent())
            val roles = accountService.getGlobalRolesForAccountGroup(group).map { it.id }
            assertEquals(listOf(Roles.GLOBAL_AUTOMATION), roles)
        }
    }

    @Test
    fun `Updating existing group roles`() {
        val name = uid("g")
        asAdmin {
            val group = accountService.createGroup(AccountGroupInput(name, "Group $name"))
            accountService.saveGlobalPermission(PermissionTargetType.GROUP,
                group.id(),
                PermissionInput(Roles.GLOBAL_ADMINISTRATOR))
            casc("""
                ontrack:
                    admin:
                        group-permissions:
                            - group: $name
                              role: AUTOMATION
            """.trimIndent())
            val roles = accountService.getGlobalRolesForAccountGroup(group).map { it.id }
            assertEquals(listOf(Roles.GLOBAL_AUTOMATION), roles)
        }
    }

    @Test
    fun `Preserving existing group roles`() {
        val existing = uid("g")
        val name = uid("g")
        asAdmin {
            val existingGroup = accountService.createGroup(AccountGroupInput(existing, "Group $existing"))
            val group = accountService.createGroup(AccountGroupInput(name, "Group $name"))
            accountService.saveGlobalPermission(PermissionTargetType.GROUP,
                existingGroup.id(),
                PermissionInput(Roles.GLOBAL_ADMINISTRATOR))
            casc("""
                ontrack:
                    admin:
                        group-permissions:
                            - group: $name
                              role: AUTOMATION
            """.trimIndent())
            // New role added
            assertEquals(listOf(Roles.GLOBAL_AUTOMATION),
                accountService.getGlobalRolesForAccountGroup(group).map { it.id })
            // Old mapping preserved
            assertEquals(listOf(Roles.GLOBAL_ADMINISTRATOR),
                accountService.getGlobalRolesForAccountGroup(existingGroup).map { it.id })
        }
    }

}