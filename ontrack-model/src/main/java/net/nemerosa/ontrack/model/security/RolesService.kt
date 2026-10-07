package net.nemerosa.ontrack.model.security

import net.nemerosa.ontrack.model.dashboards.DashboardEdition
import net.nemerosa.ontrack.model.dashboards.DashboardGlobal
import net.nemerosa.ontrack.model.dashboards.DashboardSharing
import net.nemerosa.ontrack.model.labels.LabelManagement
import net.nemerosa.ontrack.model.labels.ProjectLabelManagement
import java.util.*

/**
 * Management of roles and functions.
 *
 * @see net.nemerosa.ontrack.model.security.GlobalRole
 * @see net.nemerosa.ontrack.model.security.ProjectRole
 * @see net.nemerosa.ontrack.model.security.GlobalFunction
 * @see net.nemerosa.ontrack.model.security.ProjectFunction
 */
interface RolesService {

    /**
     * List of global roles.
     */
    val globalRoles: List<GlobalRole>

    /**
     * List of project roles
     */
    val projectRoles: List<ProjectRole>

    /**
     * List of all global functions
     */
    val globalFunctions: List<Class<out GlobalFunction>>

    /**
     * List of all project functions
     */
    val projectFunctions: List<Class<out ProjectFunction>>

    /**
     * Gets a global role by its identifier
     */
    fun getGlobalRole(id: String): Optional<GlobalRole>

    /**
     * Gets a project role by its identifier
     */
    fun getProjectRole(id: String): Optional<ProjectRole>

    /**
     * Registers a new global role.
     *
     * ℹ️ This is currently used for tests but could be used in future versions to allow the customization of roles.
     *
     * @param id ID of the role
     * @param name Display name for the role
     * @param description Short description of the role
     * @param globalFunctions List of [GlobalFunction] which are granted to this role
     * @param projectFunctions List of [ProjectFunction] which are granted to this role for _all_ projects
     */
    fun registerGlobalRole(id: String, name: String, description: String, globalFunctions: List<Class<out GlobalFunction>>, projectFunctions: List<Class<out ProjectFunction>>): GlobalRole

    /**
     * Registers a new project role
     *
     * ℹ️ This is currently used for tests but could be used in future versions to allow the customization of roles.
     *
     * @param id ID of the role
     * @param name Display name for the role
     * @param description Short description of the role
     * @param projectFunctions List of [ProjectFunction] which are granted to this role
     */
    fun registerProjectRole(id: String, name: String, description: String, projectFunctions: List<Class<out ProjectFunction>>): ProjectRole

    /**
     * Gets a project/role association
     *
     * @param project Project ID
     * @param roleId  Role name
     * @return Project/role association or [empty][java.util.Optional.empty] if the role
     * does not exist
     */
    fun getProjectRoleAssociation(project: Int, roleId: String): Optional<ProjectRoleAssociation>

    companion object {

        /**
         * List of all global functions.
         */
        val defaultGlobalFunctions = listOf(
                ProjectCreation::class.java,
                ApplicationManagement::class.java,
                GlobalSettings::class.java,
                AccountManagement::class.java,
                AccountGroupManagement::class.java,
                ProjectList::class.java,
                LabelManagement::class.java,
                ValidationStampBulkUpdate::class.java,
                DashboardEdition::class.java,
                DashboardSharing::class.java,
                DashboardGlobal::class.java,
                EventsAudit::class.java,
        )

        /**
         * List of read-only global functions.
         */
        val readOnlyGlobalFunctions = listOf<Class<out GlobalFunction>>(
                ProjectList::class.java
        )

        /**
         * List of all project functions.
         */
        val defaultProjectFunctions = listOf(
                ProjectView::class.java,
                ProjectEdit::class.java,
                ProjectDisable::class.java,
                ProjectAuthorisationMgt::class.java,
                ProjectConfig::class.java,
                ProjectDelete::class.java,
                BranchCreate::class.java,
                BranchEdit::class.java,
                BranchDisable::class.java,
                BranchFilterMgt::class.java,
                BranchDelete::class.java,
                PromotionLevelCreate::class.java,
                PromotionLevelEdit::class.java,
                PromotionLevelDelete::class.java,
                ValidationStampCreate::class.java,
                ValidationStampEdit::class.java,
                ValidationStampDelete::class.java,
                BuildCreate::class.java,
                BuildConfig::class.java,
                BuildEdit::class.java,
                BuildDelete::class.java,
                ValidationRunCreate::class.java,
                ValidationRunStatusChange::class.java,
                ValidationRunStatusCommentEditOwn::class.java,
                ValidationRunStatusCommentEdit::class.java,
                PromotionRunCreate::class.java,
                PromotionRunDelete::class.java,
                ProjectLabelManagement::class.java,
                ValidationStampFilterCreate::class.java,
                ValidationStampFilterShare::class.java,
                ValidationStampFilterMgt::class.java
        )

        /**
         * List of read-only project functions.
         */
        val readOnlyProjectFunctions = listOf<Class<out ProjectFunction>>(
                ProjectView::class.java
        )
    }
}
