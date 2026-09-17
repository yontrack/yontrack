package net.nemerosa.ontrack.boot.ui

import net.nemerosa.ontrack.extension.api.UserMenuItemExtension
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.extension.support.CoreExtensionFeature
import net.nemerosa.ontrack.model.labels.LabelManagement
import net.nemerosa.ontrack.model.security.AccountManagement
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.security.isGlobalFunctionGranted
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.springframework.stereotype.Component

@Component
class CoreUserMenuItemExtension(
    private val securityService: SecurityService,
) : AbstractExtension(CoreExtensionFeature.INSTANCE), UserMenuItemExtension {

    override val items: List<UserMenuItem>
        get() {
            val globalSettings = securityService.isGlobalFunctionGranted<GlobalSettings>()
            val accountManagement = securityService.isGlobalFunctionGranted<AccountManagement>()
            val labelManagement = securityService.isGlobalFunctionGranted<LabelManagement>()

            val items = mutableListOf<UserMenuItem>()

            items += UserMenuItem(
                groupId = CoreUserMenuGroups.USER,
                extension = "core/admin",
                id = "userProfile",
                name = "User profile",
            )

            items += UserMenuItem(
                groupId = CoreUserMenuGroups.USER,
                extension = "core/ref",
                id = "resources",
                name = "Resources",
            )

            if (globalSettings) {
                items += UserMenuItem(
                    groupId = CoreUserMenuGroups.CONFIGURATIONS,
                    extension = "core/config",
                    id = "predefined-promotion-levels",
                    name = "Predefined promotion levels",
                )
                items += UserMenuItem(
                    groupId = CoreUserMenuGroups.CONFIGURATIONS,
                    extension = "core/config",
                    id = "predefined-validation-stamps",
                    name = "Predefined validation stamps",
                )
                items += UserMenuItem(
                    groupId = CoreUserMenuGroups.SYSTEM,
                    extension = "core/admin",
                    id = "settings",
                    name = "Settings",
                )
                items += UserMenuItem(
                    groupId = CoreUserMenuGroups.SYSTEM,
                    extension = "core/admin",
                    id = "jobs",
                    name = "Background jobs",
                )
                items += UserMenuItem(
                    groupId = CoreUserMenuGroups.SYSTEM,
                    extension = "core/admin",
                    id = "health",
                    name = "System health",
                )
            }

            // Labels are managed by the holders of the LabelManagement function (administrators
            // and creators), which is a different population from the one holding GlobalSettings.
            if (labelManagement) {
                items += UserMenuItem(
                    groupId = CoreUserMenuGroups.CONFIGURATIONS,
                    extension = "core/config",
                    id = "labels",
                    name = "Labels",
                )
            }

            if (accountManagement) {
                items += UserMenuItem(
                    groupId = CoreUserMenuGroups.SYSTEM,
                    extension = "core/admin",
                    id = "account-management",
                    name = "Account management",
                )
            }

            return items
        }
}