package net.nemerosa.ontrack.extension.environments.security

import net.nemerosa.ontrack.extension.environments.EnvironmentsLicense
import net.nemerosa.ontrack.model.security.*
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

@Component
class EnvironmentsAuthorizationContributor(
    private val environmentsLicense: EnvironmentsLicense,
    private val securityService: SecurityService,
    private val structureService: StructureService,
) : AuthorizationContributor {

    companion object {
        private const val CONTEXT = "environment"

        /**
         * The global half of the `slot` context.
         *
         * Every other `slot` authorization is contributed by
         * [SlotsAuthorizationContributor] against a slot that already exists. Creating one has no
         * slot to be asked about, and the Setup page's "New slot" needs an answer before the user
         * has picked anything - hence a global entry under the same name.
         */
        private const val SLOT = "slot"
    }

    override fun appliesTo(context: Any): Boolean = context is GlobalAuthorizationContext

    override fun getAuthorizations(user: AuthenticatedUser, context: Any): List<Authorization> {
        val environmentFeatureEnabled = environmentsLicense.environmentFeatureEnabled
        return listOf(
            Authorization(
                name = CONTEXT,
                action = Authorization.VIEW,
                authorized = environmentFeatureEnabled &&
                        securityService.isGlobalFunctionGranted<EnvironmentList>()
            ),
            Authorization(
                name = CONTEXT,
                action = Authorization.CREATE,
                authorized = environmentFeatureEnabled &&
                        securityService.isGlobalFunctionGranted<EnvironmentSave>()
            ),
            Authorization(
                name = CONTEXT,
                action = Authorization.EDIT,
                authorized = environmentFeatureEnabled &&
                        securityService.isGlobalFunctionGranted<EnvironmentSave>()
            ),
            Authorization(
                name = CONTEXT,
                action = Authorization.DELETE,
                authorized = environmentFeatureEnabled &&
                        securityService.isGlobalFunctionGranted<EnvironmentDelete>()
            ),
            Authorization(
                name = SLOT,
                action = Authorization.CREATE,
                authorized = environmentFeatureEnabled && canCreateASlotSomewhere(),
            ),
        )
    }

    /**
     * Can this user create a slot at all?
     *
     * `SlotCreate` is a *project* function, and the only honest global reading of it is "on at
     * least one project". `projectList` is already narrowed to the projects the user can see, and
     * `any` stops at the first match - so an administrator costs one check, and only a user with no
     * rights anywhere pays for the whole walk. The per-project right is still what decides the
     * creation itself, in `SlotServiceImpl.addSlot`; this only decides whether a button is drawn.
     */
    private fun canCreateASlotSomewhere(): Boolean =
        structureService.projectList.any {
            securityService.isProjectFunctionGranted(it, SlotCreate::class.java)
        }

}