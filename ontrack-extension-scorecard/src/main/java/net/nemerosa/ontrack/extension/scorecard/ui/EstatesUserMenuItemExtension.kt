package net.nemerosa.ontrack.extension.scorecard.ui

import net.nemerosa.ontrack.extension.api.UserMenuItemExtension
import net.nemerosa.ontrack.extension.scorecard.ScorecardExtensionFeature
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicense
import net.nemerosa.ontrack.extension.scorecard.security.EstateManagement
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.springframework.stereotype.Component

/**
 * The estates admin page, `/extension/scorecard/estates`, in the configurations group of the user
 * menu, for the users who can manage the estates ([EstateManagement]) when the licence allows them —
 * the same conditions as the global `estate/edit` authorization.
 */
@Component
class EstatesUserMenuItemExtension(
    scorecardExtensionFeature: ScorecardExtensionFeature,
    private val scorecardLicense: ScorecardLicense,
    private val securityService: SecurityService,
) : AbstractExtension(scorecardExtensionFeature), UserMenuItemExtension {

    override val items: List<UserMenuItem>
        get() = listOfNotNull(
            UserMenuItem(
                groupId = CoreUserMenuGroups.CONFIGURATIONS,
                extension = feature,
                id = "estates",
                name = "Estates",
            ).takeIf {
                scorecardLicense.estatesEnabled &&
                        securityService.isGlobalFunctionGranted(EstateManagement::class.java)
            }
        )
}
