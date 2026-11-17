package net.nemerosa.ontrack.extension.notifications.http.config

import net.nemerosa.ontrack.extension.api.UserMenuItemExtension
import net.nemerosa.ontrack.extension.notifications.NotificationsExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.security.isGlobalFunctionGranted
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.springframework.stereotype.Component

/**
 * Entry of the HTTP configurations page in the configurations menu.
 */
@Component
class HttpConfigurationUserMenuItemExtension(
    notificationsExtensionFeature: NotificationsExtensionFeature,
    private val securityService: SecurityService,
) : AbstractExtension(notificationsExtensionFeature), UserMenuItemExtension {
    override val items: List<UserMenuItem>
        get() = listOfNotNull(
            if (securityService.isGlobalFunctionGranted<GlobalSettings>()) {
                UserMenuItem(
                    groupId = CoreUserMenuGroups.CONFIGURATIONS,
                    extension = "extension/${feature.id}",
                    id = "http/configurations",
                    name = "HTTP configurations",
                )
            } else {
                null
            }
        )
}
