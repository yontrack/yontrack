package net.nemerosa.ontrack.extension.casc.context.core.admin

import net.nemerosa.ontrack.extension.casc.AbstractCascTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.json.schema.JsonTypeBuilder
import net.nemerosa.ontrack.model.security.AccountGroup
import net.nemerosa.ontrack.model.security.PermissionTargetType
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals

class ProjectPermissionsAdminContextIT : AbstractCascTestSupport() {

    @Autowired
    private lateinit var context: ProjectPermissionsAdminContext

    @Autowired
    private lateinit var jsonTypeBuilder: JsonTypeBuilder

    @Test
    fun `CasC schema type`() {
        val type = context.jsonType(jsonTypeBuilder)
        assertEquals(
            """
                {
                  "items": {
                    "title": "ProjectPermission",
                    "properties": {
                      "group": {
                        "description": "Name of the group",
                        "type": "string"
                      },
                      "projects": {
                        "items": {
                          "description": "List of projects",
                          "type": "string"
                        },
                        "description": "List of projects",
                        "type": "array"
                      },
                      "role": {
                        "description": "Name of the role to assign",
                        "type": "string"
                      }
                    },
                    "required": [
                      "group",
                      "projects",
                      "role"
                    ],
                    "additionalProperties": false,
                    "type": "object"
                  },
                  "description": "List of permissions per group. A listed group and project pair gets exactly the listed roles, the other pairs are left untouched.",
                  "type": "array"
                }
            """.trimIndent().parseAsJson(),
            type.asJson()
        )
    }

    @Test
    fun `Rendering of permissions`() {
        asAdmin {
            // Existing items
            val p1 = project()
            val p2 = project()
            val p3 = project()
            val group1 = doCreateAccountGroup()
            val group2 = doCreateAccountGroup()
            val group3 = doCreateAccountGroup()
            // Different roles per project, and two roles for one group on one project
            accountService.grantProjectPermission(p1.id, PermissionTargetType.GROUP, group1.id(), Roles.PROJECT_OWNER)
            accountService.grantProjectPermission(p3.id, PermissionTargetType.GROUP, group1.id(), Roles.PROJECT_OWNER)
            accountService.grantProjectPermission(
                p1.id,
                PermissionTargetType.GROUP,
                group2.id(),
                Roles.PROJECT_VALIDATION_MANAGER
            )
            accountService.grantProjectPermission(p1.id, PermissionTargetType.GROUP, group2.id(), Roles.PROJECT_PROMOTER)
            accountService.grantProjectPermission(p2.id, PermissionTargetType.GROUP, group3.id(), Roles.PROJECT_OWNER)
            // Rendering, restricted to the groups of this test
            val groups = setOf(group1.name, group2.name, group3.name)
            val rendered = context.render()
            assertEquals(
                setOf(
                    Triple(group1.name, Roles.PROJECT_OWNER, setOf(p1.name, p3.name)),
                    Triple(group2.name, Roles.PROJECT_VALIDATION_MANAGER, setOf(p1.name)),
                    Triple(group2.name, Roles.PROJECT_PROMOTER, setOf(p1.name)),
                    Triple(group3.name, Roles.PROJECT_OWNER, setOf(p2.name)),
                ),
                rendered.renderedPermissions(groups)
            )
        }
    }

    @Test
    fun `Re-applying the rendered permissions keeps every role`() {
        asAdmin {
            val p1 = project()
            val p2 = project()
            val group = doCreateAccountGroup()
            accountService.grantProjectPermission(p1.id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
            accountService.grantProjectPermission(p1.id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PROMOTER)
            accountService.grantProjectPermission(p2.id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
            // Rendering only the permissions of this group, as an export would
            val rendered = context.render().filter { it.path("group").asString() == group.name }
            // Re-applying the export (JSON is YAML)
            casc(
                mapOf(
                    "ontrack" to mapOf(
                        "admin" to mapOf(
                            "project-permissions" to rendered
                        )
                    )
                ).asJson().toString()
            )
            assertEquals(
                setOf(Roles.PROJECT_PARTICIPANT, Roles.PROJECT_PROMOTER),
                rolesOf(group, p1)
            )
            assertEquals(setOf(Roles.PROJECT_PARTICIPANT), rolesOf(group, p2))
        }
    }

    @Test
    fun `Setting up permissions on two existing projects and one existing group`() {
        asAdmin {
            // Existing items
            val p1 = project()
            val p2 = project()
            val group = doCreateAccountGroup()
            // Running the Casc config
            casc(
                """
                ontrack:
                    admin:
                        project-permissions:
                            - group: ${group.name}
                              role: ${Roles.PROJECT_OWNER}
                              projects:
                                - ${p1.name}
                                - ${p2.name}
                """.trimIndent()
            )
            // Checks the permissions have been set
            val associations = accountService.getProjectPermissionsForAccountGroup(group).associate {
                it.projectId to it.projectRole
            }
            assertEquals(Roles.PROJECT_OWNER, associations[p1.id()]?.id)
            assertEquals(Roles.PROJECT_OWNER, associations[p2.id()]?.id)
        }
    }

    @Test
    fun `Setting up permissions on one existing project, one non-existing project and one existing group`() {
        asAdmin {
            // Existing items
            val p1 = project()
            val p2 = uid("p")
            val group = doCreateAccountGroup()
            // Running the Casc config
            casc(
                """
                ontrack:
                    admin:
                        project-permissions:
                            - group: ${group.name}
                              role: ${Roles.PROJECT_OWNER}
                              projects:
                                - ${p1.name}
                                - $p2
                """.trimIndent()
            )
            // Checks the permissions have been set
            val associations = accountService.getProjectPermissionsForAccountGroup(group).associate {
                it.projectId to it.projectRole
            }
            assertEquals(1, associations.size)
            assertEquals(Roles.PROJECT_OWNER, associations[p1.id()]?.id)
        }
    }

    @Test
    fun `Setting up permissions on one existing project and a non existing group`() {
        asAdmin {
            // Existing items
            val p1 = project()
            val group = uid("g")
            // Running the Casc config
            casc(
                """
                ontrack:
                    admin:
                        project-permissions:
                            - group: $group
                              role: ${Roles.PROJECT_OWNER}
                              projects:
                                - ${p1.name}
                """.trimIndent()
            )
            // Checks the permissions have been set
            val associations = accountService.getProjectPermissions(p1.id)
            assertEquals(0, associations.size)
        }
    }

    @Test
    fun `Setting up permissions on two existing projects and one existing group and a non existing role`() {
        asAdmin {
            // Existing items
            val p1 = project()
            val p2 = project()
            val group = doCreateAccountGroup()
            // Running the Casc config
            casc(
                """
                ontrack:
                    admin:
                        project-permissions:
                            - group: ${group.name}
                              role: xxxx
                              projects:
                                - ${p1.name}
                                - ${p2.name}
                """.trimIndent()
            )
            // Checks the permissions have been set
            val associations = accountService.getProjectPermissionsForAccountGroup(group).associate {
                it.projectId to it.projectRole
            }
            assertEquals(0, associations.size)
        }
    }

    @Test
    fun `Existing permissions are preserved`() {
        asAdmin {
            // Existing items
            val p1 = project()
            val p2 = project()
            val group = doCreateAccountGroup()
            // Running the Casc config (first run)
            casc(
                """
                ontrack:
                    admin:
                        project-permissions:
                            - group: ${group.name}
                              role: ${Roles.PROJECT_OWNER}
                              projects:
                                - ${p1.name}
                                - ${p2.name}
                """.trimIndent()
            )
            // Checks the permissions have been set
            var associations = accountService.getProjectPermissionsForAccountGroup(group).associate {
                it.projectId to it.projectRole
            }
            assertEquals(Roles.PROJECT_OWNER, associations[p1.id()]?.id)
            assertEquals(Roles.PROJECT_OWNER, associations[p2.id()]?.id)
            // Running the Casc config (second run)
            casc(
                """
                ontrack:
                    admin:
                        project-permissions:
                            - group: ${group.name}
                              role: ${Roles.PROJECT_OWNER}
                              projects:
                                - ${p1.name}
                """.trimIndent()
            )
            // Checks the permissions have been set
            associations = accountService.getProjectPermissionsForAccountGroup(group).associate {
                it.projectId to it.projectRole
            }
            assertEquals(Roles.PROJECT_OWNER, associations[p1.id()]?.id)
            assertEquals(Roles.PROJECT_OWNER, associations[p2.id()]?.id)
        }
    }

    @Test
    fun `Two entries for one group and project give two roles`() {
        asAdmin {
            val p1 = project()
            val p2 = project()
            val group = doCreateAccountGroup()
            casc(
                """
                ontrack:
                    admin:
                        project-permissions:
                            - group: ${group.name}
                              role: ${Roles.PROJECT_PARTICIPANT}
                              projects:
                                - ${p1.name}
                                - ${p2.name}
                            - group: ${group.name}
                              role: ${Roles.PROJECT_PROMOTER}
                              projects:
                                - ${p1.name}
                """.trimIndent()
            )
            assertEquals(setOf(Roles.PROJECT_PARTICIPANT, Roles.PROJECT_PROMOTER), rolesOf(group, p1))
            assertEquals(setOf(Roles.PROJECT_PARTICIPANT), rolesOf(group, p2))
        }
    }

    @Test
    fun `Removing an entry revokes this role only on this project`() {
        asAdmin {
            val p1 = project()
            val p2 = project()
            val group = doCreateAccountGroup()
            casc(
                """
                ontrack:
                    admin:
                        project-permissions:
                            - group: ${group.name}
                              role: ${Roles.PROJECT_PARTICIPANT}
                              projects:
                                - ${p1.name}
                                - ${p2.name}
                            - group: ${group.name}
                              role: ${Roles.PROJECT_PROMOTER}
                              projects:
                                - ${p1.name}
                """.trimIndent()
            )
            // Rerun without the PROMOTER entry
            casc(
                """
                ontrack:
                    admin:
                        project-permissions:
                            - group: ${group.name}
                              role: ${Roles.PROJECT_PARTICIPANT}
                              projects:
                                - ${p1.name}
                                - ${p2.name}
                """.trimIndent()
            )
            assertEquals(setOf(Roles.PROJECT_PARTICIPANT), rolesOf(group, p1))
            assertEquals(setOf(Roles.PROJECT_PARTICIPANT), rolesOf(group, p2))
        }
    }

    @Test
    fun `A listed group and project pair gets exactly the listed roles`() {
        asAdmin {
            val p1 = project()
            val group = doCreateAccountGroup()
            // Granted outside CasC
            accountService.grantProjectPermission(p1.id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
            accountService.grantProjectPermission(p1.id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_OWNER)
            casc(
                """
                ontrack:
                    admin:
                        project-permissions:
                            - group: ${group.name}
                              role: ${Roles.PROJECT_PARTICIPANT}
                              projects:
                                - ${p1.name}
                            - group: ${group.name}
                              role: ${Roles.PROJECT_PROMOTER}
                              projects:
                                - ${p1.name}
                """.trimIndent()
            )
            // PARTICIPANT kept, PROMOTER granted, OWNER revoked
            assertEquals(setOf(Roles.PROJECT_PARTICIPANT, Roles.PROJECT_PROMOTER), rolesOf(group, p1))
        }
    }

    @Test
    fun `An unlisted group and project pair keeps the roles granted outside CasC`() {
        asAdmin {
            val p1 = project()
            val p2 = project()
            val group = doCreateAccountGroup()
            val other = doCreateAccountGroup()
            // Granted outside CasC: the group on another project, another group on the same project
            accountService.grantProjectPermission(p2.id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_OWNER)
            accountService.grantProjectPermission(p2.id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PROMOTER)
            accountService.grantProjectPermission(p1.id, PermissionTargetType.GROUP, other.id(), Roles.PROJECT_OWNER)
            accountService.grantProjectPermission(p1.id, PermissionTargetType.GROUP, other.id(), Roles.PROJECT_PROMOTER)
            casc(
                """
                ontrack:
                    admin:
                        project-permissions:
                            - group: ${group.name}
                              role: ${Roles.PROJECT_PARTICIPANT}
                              projects:
                                - ${p1.name}
                """.trimIndent()
            )
            assertEquals(setOf(Roles.PROJECT_PARTICIPANT), rolesOf(group, p1))
            assertEquals(setOf(Roles.PROJECT_OWNER, Roles.PROJECT_PROMOTER), rolesOf(group, p2))
            assertEquals(setOf(Roles.PROJECT_OWNER, Roles.PROJECT_PROMOTER), rolesOf(other, p1))
        }
    }

    /**
     * IDs of the roles [group] holds on [project].
     */
    private fun rolesOf(group: AccountGroup, project: Project): Set<String> =
        accountService.getProjectPermissionsForAccountGroup(group)
            .filter { it.projectId == project.id() }
            .map { it.projectRole.id }
            .toSet()

    /**
     * The rendered entries of the given [groups], as (group, role, projects).
     */
    private fun JsonNode.renderedPermissions(groups: Set<String>): Set<Triple<String, String, Set<String>>> =
        filter { it.path("group").asString() in groups }
            .map { node ->
                Triple(
                    node.path("group").asString(),
                    node.path("role").asString(),
                    node.path("projects").values().map { project: JsonNode -> project.asString() }.toSet(),
                )
            }
            .toSet()

}
