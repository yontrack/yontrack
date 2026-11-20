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
                  "description": "List of account groups global permissions. A listed group gets exactly the listed roles, the other groups are left untouched.",
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

    @Test
    fun `Two entries for one group give two roles`() {
        val name = uid("g")
        asAdmin {
            val group = accountService.createGroup(AccountGroupInput(name, "Group $name"))
            casc("""
                ontrack:
                    admin:
                        group-permissions:
                            - group: $name
                              role: ${Roles.GLOBAL_PARTICIPANT}
                            - group: $name
                              role: ${Roles.GLOBAL_AUTOMATION}
            """.trimIndent())
            assertEquals(
                setOf(Roles.GLOBAL_PARTICIPANT, Roles.GLOBAL_AUTOMATION),
                accountService.getGlobalRolesForAccountGroup(group).map { it.id }.toSet()
            )
        }
    }

    @Test
    fun `Removing an entry revokes this role only`() {
        val name = uid("g")
        asAdmin {
            val group = accountService.createGroup(AccountGroupInput(name, "Group $name"))
            casc("""
                ontrack:
                    admin:
                        group-permissions:
                            - group: $name
                              role: ${Roles.GLOBAL_PARTICIPANT}
                            - group: $name
                              role: ${Roles.GLOBAL_AUTOMATION}
            """.trimIndent())
            casc("""
                ontrack:
                    admin:
                        group-permissions:
                            - group: $name
                              role: ${Roles.GLOBAL_PARTICIPANT}
            """.trimIndent())
            assertEquals(
                listOf(Roles.GLOBAL_PARTICIPANT),
                accountService.getGlobalRolesForAccountGroup(group).map { it.id }
            )
        }
    }

    @Test
    fun `A listed group gets exactly the listed roles`() {
        val name = uid("g")
        asAdmin {
            val group = accountService.createGroup(AccountGroupInput(name, "Group $name"))
            // Granted outside CasC
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_PARTICIPANT)
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_CREATOR)
            casc("""
                ontrack:
                    admin:
                        group-permissions:
                            - group: $name
                              role: ${Roles.GLOBAL_PARTICIPANT}
                            - group: $name
                              role: ${Roles.GLOBAL_AUTOMATION}
            """.trimIndent())
            // PARTICIPANT kept, AUTOMATION granted, CREATOR revoked
            assertEquals(
                setOf(Roles.GLOBAL_PARTICIPANT, Roles.GLOBAL_AUTOMATION),
                accountService.getGlobalRolesForAccountGroup(group).map { it.id }.toSet()
            )
        }
    }

    @Test
    fun `An unlisted group keeps all the roles granted outside CasC`() {
        val existing = uid("g")
        val name = uid("g")
        asAdmin {
            val existingGroup = accountService.createGroup(AccountGroupInput(existing, "Group $existing"))
            val group = accountService.createGroup(AccountGroupInput(name, "Group $name"))
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, existingGroup.id(), Roles.GLOBAL_PARTICIPANT)
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, existingGroup.id(), Roles.GLOBAL_CREATOR)
            casc("""
                ontrack:
                    admin:
                        group-permissions:
                            - group: $name
                              role: ${Roles.GLOBAL_AUTOMATION}
            """.trimIndent())
            assertEquals(
                listOf(Roles.GLOBAL_AUTOMATION),
                accountService.getGlobalRolesForAccountGroup(group).map { it.id }
            )
            assertEquals(
                setOf(Roles.GLOBAL_PARTICIPANT, Roles.GLOBAL_CREATOR),
                accountService.getGlobalRolesForAccountGroup(existingGroup).map { it.id }.toSet()
            )
        }
    }

    @Test
    fun `Rendering emits one entry per group and role, and re-applying it keeps every role`() {
        val name = uid("g")
        asAdmin {
            val group = accountService.createGroup(AccountGroupInput(name, "Group $name"))
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_PARTICIPANT)
            accountService.grantGlobalPermission(PermissionTargetType.GROUP, group.id(), Roles.GLOBAL_CREATOR)
            // Rendering
            val rendered = accountGroupGlobalPermissionsAdminContext.render()
            assertEquals(
                setOf(
                    mapOf("group" to name, "role" to Roles.GLOBAL_PARTICIPANT),
                    mapOf("group" to name, "role" to Roles.GLOBAL_CREATOR),
                ),
                rendered.filter { it.path("group").asString() == name }
                    .map { mapOf("group" to it.path("group").asString(), "role" to it.path("role").asString()) }
                    .toSet()
            )
            // Re-applying the export (JSON is YAML)
            casc(
                mapOf(
                    "ontrack" to mapOf(
                        "admin" to mapOf(
                            "group-permissions" to rendered
                        )
                    )
                ).asJson().toString()
            )
            assertEquals(
                setOf(Roles.GLOBAL_PARTICIPANT, Roles.GLOBAL_CREATOR),
                accountService.getGlobalRolesForAccountGroup(group).map { it.id }.toSet()
            )
        }
    }

}
