package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.security.PermissionTarget
import net.nemerosa.ontrack.model.security.PermissionTargetType
import net.nemerosa.ontrack.model.security.ProjectAuthorisationMgt
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.structure.Project
import org.junit.jupiter.api.Test
import org.springframework.graphql.execution.ErrorType
import kotlin.test.assertEquals

/**
 * Project roles add up (ADR 0023): the GraphQL mutations granting and revoking one project role
 * of an account or a group, and the reads listing them.
 */
class ProjectRolesMutationsIT : AbstractQLKTITSupport() {

    @Test
    fun `Granting two project roles to an account`() {
        asAdmin {
            project {
                val account = doCreateAccount()
                listOf(Roles.PROJECT_PARTICIPANT, Roles.PROJECT_PROMOTER).forEach { role ->
                    run(
                        """
                            mutation {
                                grantProjectRoleToAccount(input: {
                                    projectId: $id,
                                    accountId: ${account.id},
                                    projectRole: "$role"
                                }) {
                                    errors { message }
                                }
                            }
                        """
                    ) { data ->
                        checkGraphQLUserErrors(data, "grantProjectRoleToAccount")
                    }
                }
                assertEquals(
                    listOf(Roles.PROJECT_PARTICIPANT, Roles.PROJECT_PROMOTER),
                    rolesOf(this, account.asPermissionTarget())
                )
            }
        }
    }

    @Test
    fun `Granting two project roles to an account group`() {
        asAdmin {
            project {
                val group = doCreateAccountGroup()
                listOf(Roles.PROJECT_PARTICIPANT, Roles.PROJECT_VALIDATION_MANAGER).forEach { role ->
                    grantToGroup(this, group.id(), role)
                }
                // Granting a role twice changes nothing
                grantToGroup(this, group.id(), Roles.PROJECT_PARTICIPANT)
                assertEquals(
                    listOf(Roles.PROJECT_PARTICIPANT, Roles.PROJECT_VALIDATION_MANAGER),
                    rolesOf(this, group.asPermissionTarget())
                )
            }
        }
    }

    @Test
    fun `Revoking one project role from an account`() {
        asAdmin {
            project {
                val account = doCreateAccount()
                accountService.grantProjectPermission(id, PermissionTargetType.ACCOUNT, account.id(), Roles.PROJECT_PARTICIPANT)
                accountService.grantProjectPermission(id, PermissionTargetType.ACCOUNT, account.id(), Roles.PROJECT_PROMOTER)
                run(
                    """
                        mutation {
                            revokeProjectRoleFromAccount(input: {
                                projectId: $id,
                                accountId: ${account.id},
                                projectRole: "${Roles.PROJECT_PARTICIPANT}"
                            }) {
                                errors { message }
                            }
                        }
                    """
                ) { data ->
                    checkGraphQLUserErrors(data, "revokeProjectRoleFromAccount")
                }
                assertEquals(
                    listOf(Roles.PROJECT_PROMOTER),
                    rolesOf(this, account.asPermissionTarget())
                )
            }
        }
    }

    @Test
    fun `Revoking one project role from an account group`() {
        asAdmin {
            project {
                val group = doCreateAccountGroup()
                accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
                accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_VALIDATION_MANAGER)
                run(
                    """
                        mutation {
                            revokeProjectRoleFromAccountGroup(input: {
                                projectId: $id,
                                accountGroupId: ${group.id},
                                projectRole: "${Roles.PROJECT_VALIDATION_MANAGER}"
                            }) {
                                errors { message }
                            }
                        }
                    """
                ) { data ->
                    checkGraphQLUserErrors(data, "revokeProjectRoleFromAccountGroup")
                }
                assertEquals(
                    listOf(Roles.PROJECT_PARTICIPANT),
                    rolesOf(this, group.asPermissionTarget())
                )
            }
        }
    }

    @Test
    fun `Granting an unknown project role is a user error`() {
        asAdmin {
            project {
                val group = doCreateAccountGroup()
                run(
                    """
                        mutation {
                            grantProjectRoleToAccountGroup(input: {
                                projectId: $id,
                                accountGroupId: ${group.id},
                                projectRole: "UNKNOWN_ROLE"
                            }) {
                                errors { message exception }
                            }
                        }
                    """
                ) { data ->
                    assertUserError(
                        data, "grantProjectRoleToAccountGroup",
                        message = "Role not found: UNKNOWN_ROLE",
                        exception = "net.nemerosa.ontrack.model.exceptions.RoleNotFoundException",
                    )
                }
                assertEquals(emptyList(), rolesOf(this, group.asPermissionTarget()))
            }
        }
    }

    @Test
    fun `The four mutations need the project authorisation management`() {
        asAdmin {
            project {
                val account = doCreateAccount()
                val group = doCreateAccountGroup()
                accountService.grantProjectPermission(id, PermissionTargetType.ACCOUNT, account.id(), Roles.PROJECT_PARTICIPANT)
                accountService.grantProjectPermission(id, PermissionTargetType.GROUP, group.id(), Roles.PROJECT_PARTICIPANT)
                val mutations = listOf(
                    "grantProjectRoleToAccount" to "accountId: ${account.id}, projectRole: \"${Roles.PROJECT_OWNER}\"",
                    "revokeProjectRoleFromAccount" to "accountId: ${account.id}, projectRole: \"${Roles.PROJECT_PARTICIPANT}\"",
                    "grantProjectRoleToAccountGroup" to "accountGroupId: ${group.id}, projectRole: \"${Roles.PROJECT_OWNER}\"",
                    "revokeProjectRoleFromAccountGroup" to "accountGroupId: ${group.id}, projectRole: \"${Roles.PROJECT_PARTICIPANT}\"",
                )
                // A project manager sees the project but cannot manage its permissions
                val manager = doCreateAccountWithProjectRole(this, Roles.PROJECT_MANAGER)
                asFixedAccount(manager) {
                    mutations.forEach { (name, arguments) ->
                        runWithError(
                            """
                                mutation {
                                    $name(input: { projectId: $id, $arguments }) {
                                        errors { message }
                                    }
                                }
                            """,
                            errorClassification = ErrorType.FORBIDDEN,
                        )
                    }
                }
                assertEquals(listOf(Roles.PROJECT_PARTICIPANT), rolesOf(this, account.asPermissionTarget()))
                assertEquals(listOf(Roles.PROJECT_PARTICIPANT), rolesOf(this, group.asPermissionTarget()))
                // With the project authorisation management, it works
                asUser().with(id(), ProjectAuthorisationMgt::class.java).call {
                    mutations.forEach { (name, arguments) ->
                        run(
                            """
                                mutation {
                                    $name(input: { projectId: $id, $arguments }) {
                                        errors { message }
                                    }
                                }
                            """
                        ) { data ->
                            checkGraphQLUserErrors(data, name)
                        }
                    }
                }
                assertEquals(listOf(Roles.PROJECT_OWNER), rolesOf(this, account.asPermissionTarget()))
                assertEquals(listOf(Roles.PROJECT_OWNER), rolesOf(this, group.asPermissionTarget()))
            }
        }
    }

    @Test
    fun `Project roles list a group under each of its roles`() {
        asAdmin {
            project {
                val group = doCreateAccountGroup()
                grantToGroup(this, group.id(), Roles.PROJECT_PARTICIPANT)
                grantToGroup(this, group.id(), Roles.PROJECT_VALIDATION_MANAGER)
                run(
                    """
                        {
                            project(id: $id) {
                                projectRoles {
                                    id
                                    groups { name }
                                }
                            }
                        }
                    """
                ) { data ->
                    val rolesOfGroup = data.path("project").path("projectRoles")
                        .filter { role -> role.path("groups").any { it.path("name").asText() == group.name } }
                        .map { it.path("id").asText() }
                        .sorted()
                    assertEquals(
                        listOf(Roles.PROJECT_PARTICIPANT, Roles.PROJECT_VALIDATION_MANAGER),
                        rolesOfGroup
                    )
                }
            }
        }
    }

    @Test
    fun `Authorized projects list each role of an account group on a project`() {
        asAdmin {
            project {
                val group = doCreateAccountGroup()
                grantToGroup(this, group.id(), Roles.PROJECT_PARTICIPANT)
                grantToGroup(this, group.id(), Roles.PROJECT_VALIDATION_MANAGER)
                run(
                    """
                        {
                            accountGroups(id: ${group.id}) {
                                authorizedProjects {
                                    role { id }
                                    project { name }
                                }
                            }
                        }
                    """
                ) { data ->
                    val authorizedProjects: List<Pair<String, String>> =
                        data.path("accountGroups").first().path("authorizedProjects").asIterable()
                            .map { node -> node.path("project").path("name").asText() to node.path("role").path("id").asText() }
                    assertEquals(
                        listOf(
                            name to Roles.PROJECT_PARTICIPANT,
                            name to Roles.PROJECT_VALIDATION_MANAGER,
                        ),
                        authorizedProjects.sortedBy { pair -> pair.second }
                    )
                }
            }
        }
    }

    private fun grantToGroup(project: Project, groupId: Int, role: String) {
        run(
            """
                mutation {
                    grantProjectRoleToAccountGroup(input: {
                        projectId: ${project.id},
                        accountGroupId: $groupId,
                        projectRole: "$role"
                    }) {
                        errors { message }
                    }
                }
            """
        ) { data ->
            checkGraphQLUserErrors(data, "grantProjectRoleToAccountGroup")
        }
    }

    private fun rolesOf(project: Project, target: PermissionTarget): List<String> =
        accountService.getProjectPermissions(project.id)
            .filter { it.target.type == target.type && it.target.id == target.id }
            .map { it.role.id }
            .sorted()
}
