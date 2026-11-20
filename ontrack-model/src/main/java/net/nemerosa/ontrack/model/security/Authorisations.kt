package net.nemerosa.ontrack.model.security;

import java.io.Serializable
import kotlin.reflect.KClass

/**
 * What an account or a group is granted: functions, global roles and project roles. Roles add up
 * (ADR 0023): the functions granted are the union of those of all the roles.
 */
data class Authorisations(
    private val projectFunctions: Set<KClass<out ProjectFunction>> = emptySet(),
    private val globalFunctions: Set<KClass<out GlobalFunction>> = emptySet(),
    private val globalRoles: Set<GlobalRole> = emptySet(),
    private val projectRoleAssociations: Set<ProjectRoleAssociation> = emptySet()
) : AuthorisationsCheck, Serializable {

    companion object {
        @JvmStatic
        fun none() = Authorisations(
            projectFunctions = emptySet(),
            globalFunctions = emptySet(),
            globalRoles = emptySet(),
            projectRoleAssociations = emptySet(),
        )
    }

    override fun isGranted(fn: Class<out GlobalFunction>) =
        globalFunctions.map { it.java }.any { fn.isAssignableFrom(it) } ||
                globalRoles.any { it.isGlobalFunctionGranted(fn) }

    override fun isGranted(projectId: Int, fn: Class<out ProjectFunction>) =
        globalRoles.any { it.isProjectFunctionGranted(fn) }
                || projectFunctions.map { it.java }.any { fn.isAssignableFrom(it) }
                || projectRoleAssociations.any { pa -> pa.projectId == projectId && pa.isGranted(fn) }

    fun withProjectFunctions(projectFunctions: Set<KClass<out ProjectFunction>>) =
        copy(projectFunctions = projectFunctions)

    fun withGlobalFunctions(globalFunctions: Set<KClass<out GlobalFunction>>) =
        copy(globalFunctions = globalFunctions)

    /**
     * Adds a global role to the ones already there. A null role adds nothing.
     */
    fun withGlobalRole(globalRole: GlobalRole?) =
        if (globalRole != null) copy(globalRoles = globalRoles + globalRole) else this

    /**
     * Adds global roles to the ones already there.
     */
    fun withGlobalRoles(globalRoles: Collection<GlobalRole>) =
        copy(globalRoles = this.globalRoles + globalRoles)

    fun withProjectRoles(projectRoleAssociations: Collection<ProjectRoleAssociation>) =
        copy(projectRoleAssociations = this.projectRoleAssociations + projectRoleAssociations)

    fun withProjectRole(projectRoleAssociation: ProjectRoleAssociation) =
        copy(projectRoleAssociations = this.projectRoleAssociations + projectRoleAssociation)
}
