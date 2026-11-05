package net.nemerosa.ontrack.extension.findings.ui

import net.nemerosa.ontrack.extension.api.UserMenuItemExtension
import net.nemerosa.ontrack.extension.findings.FindingsExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.springframework.stereotype.Component

/**
 * The page following one finding across all the projects, for every authenticated user: the
 * projects whose findings a user cannot see are left out of it.
 */
@Component
class FindingFanOutUserMenuItemExtension(
    findingsExtensionFeature: FindingsExtensionFeature,
) : AbstractExtension(findingsExtensionFeature), UserMenuItemExtension {

    override val items: List<UserMenuItem> = listOf(
        UserMenuItem(
            groupId = CoreUserMenuGroups.INFORMATION,
            extension = findingsExtensionFeature,
            id = "fan-out",
            name = "Finding across projects",
        )
    )
}
