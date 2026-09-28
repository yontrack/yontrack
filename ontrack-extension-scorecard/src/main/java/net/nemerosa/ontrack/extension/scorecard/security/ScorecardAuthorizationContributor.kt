package net.nemerosa.ontrack.extension.scorecard.security

import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicense
import net.nemerosa.ontrack.model.security.*
import org.springframework.stereotype.Component

/**
 * Global `estate` authorizations, for the UI to show what the user can do with the estates:
 * `estate/view` for every user, `estate/edit` with [EstateManagement] — both only when the
 * licence allows the estates.
 */
@Component
class ScorecardAuthorizationContributor(
    private val scorecardLicense: ScorecardLicense,
    private val securityService: SecurityService,
) : AuthorizationContributor {

    override fun appliesTo(context: Any): Boolean = context is GlobalAuthorizationContext

    override fun getAuthorizations(user: AuthenticatedUser, context: Any): List<Authorization> {
        val enabled = scorecardLicense.estatesEnabled
        return listOf(
            Authorization(
                name = ESTATE,
                action = Authorization.VIEW,
                authorized = enabled,
            ),
            Authorization(
                name = ESTATE,
                action = Authorization.EDIT,
                authorized = enabled && securityService.isGlobalFunctionGranted(EstateManagement::class.java),
            ),
        )
    }

    companion object {
        const val ESTATE = "estate"
    }
}
