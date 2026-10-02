package net.nemerosa.ontrack.extension.scorecard.ui

import net.nemerosa.ontrack.extension.api.UserMenuItemExtension
import net.nemerosa.ontrack.extension.scorecard.ScorecardExtensionFeature
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicense
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.springframework.stereotype.Component

/**
 * The scorecards of the estates, `/extension/scorecard/scorecards`, in the information group of the
 * user menu, when the licence allows the estates. The estates are readable by every authenticated
 * user, and the readings of each project are filtered by the right to see it.
 */
@Component
class ScorecardsUserMenuItemExtension(
    scorecardExtensionFeature: ScorecardExtensionFeature,
    private val scorecardLicense: ScorecardLicense,
) : AbstractExtension(scorecardExtensionFeature), UserMenuItemExtension {

    override val items: List<UserMenuItem>
        get() = listOfNotNull(
            UserMenuItem(
                groupId = CoreUserMenuGroups.INFORMATION,
                extension = feature,
                id = "scorecards",
                name = "Scorecards",
            ).takeIf {
                scorecardLicense.estatesEnabled
            }
        )
}
