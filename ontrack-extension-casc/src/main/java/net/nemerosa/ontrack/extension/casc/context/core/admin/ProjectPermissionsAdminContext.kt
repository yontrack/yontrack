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
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

/**
 * Project permissions as code.
 */
@Component
class ProjectPermissionsAdminContext(
    private val structureService: StructureService,
    private val accountService: AccountService,
    private val rolesService: RolesService,
) : AbstractCascContext(), SubAdminContext {

    private val logger: Logger = LoggerFactory.getLogger(ProjectPermissionsAdminContext::class.java)

    override val field: String = "project-permissions"

    override fun jsonType(jsonTypeBuilder: JsonTypeBuilder): JsonType {
        return JsonArrayType(
            description = "List of permissions per group",
            items = jsonTypeBuilder.toType(ProjectPermission::class)
        )
    }

    override fun run(node: JsonNode, paths: List<String>) {
        val permissions = node.mapIndexed { index, child ->
            try {
                child.parse<ProjectPermission>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${ProjectPermission::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }
        // Indexing all project roles
        val projectRoles = rolesService.projectRoles.associateBy { it.id }
        // For each permission
        permissions.forEach { permission ->
            // Gets the group if it exists
            val group = accountService.findAccountGroupByName(permission.group)
            if (group != null) {
                // Gets the role if it exists
                val projectRole = projectRoles[permission.role]
                if (projectRole != null) {
                    // For each project
                    permission.projects.forEach { projectName ->
                        // Getting the project
                        val project = structureService.findProjectByName(projectName).getOrNull()
                        if (project != null) {
                            // Setting the permissions
                            logger.info("Granting role ${projectRole.id} to group ${group.name} in project ${project.name}.")
                            accountService.saveProjectPermission(
                                project.id,
                                PermissionTargetType.GROUP,
                                group.id(),
                                PermissionInput(role = projectRole.id),
                            )
                        } else {
                            logger.info("Project $projectName in project permissions does not exist (yet). Skipping for now.")
                        }
                    }
                } else {
                    logger.info("Project role ${permission.role} in project permissions does not exist (yet). Skipping for now.")
                }
            } else {
                logger.info("Account group ${permission.group} in project permissions does not exist (yet). Skipping for now.")
            }
        }
    }

    override fun render(): JsonNode {
        // Gets all the groups
        val groups = accountService.accountGroups
        // Collects the permissions for each of these groups
        val permissions = mutableMapOf<Pair<String, String>, MutableList<String>>()
        groups.forEach { group ->
            val roleAssociations: Collection<ProjectRoleAssociation> =
                accountService.getProjectPermissionsForAccountGroup(group)
            roleAssociations.forEach { roleAssociation ->
                val project = structureService.findProjectByID(ID.of(roleAssociation.projectId))
                if (project != null) {
                    val role = roleAssociation.projectRole.id
                    val projectList = permissions[group.name to role]
                    if (projectList != null) {
                        projectList += project.name
                    } else {
                        permissions[group.name to role] = mutableListOf(project.name)
                    }
                }
            }
        }
        // Conversion into the model
        val projectPermissions = permissions.map { (association, projectList) ->
            val (group, role) = association
            ProjectPermission(
                group = group,
                role = role,
                projects = projectList,
            )
        }
        // Conversion to JSON
        return projectPermissions.asJson()
    }

    data class ProjectPermission(
        @APIDescription("Name of the group")
        val group: String,
        @APIDescription("Name of the role to assign")
        val role: String,
        @APIDescription("List of projects")
        val projects: List<String>,
    )

}