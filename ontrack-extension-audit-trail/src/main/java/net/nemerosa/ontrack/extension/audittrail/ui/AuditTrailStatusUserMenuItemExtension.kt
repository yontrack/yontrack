package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.extension.api.UserMenuItemExtension
import net.nemerosa.ontrack.extension.audittrail.AuditTrailExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtension
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.springframework.stereotype.Component

/**
 * The audit trail status page, `/extension/audit-trail/status`, in the system group of the user
 * menu, for those with the global settings — licence on or off, since the page says which.
 */
@Component
class AuditTrailStatusUserMenuItemExtension(
    extensionFeature: AuditTrailExtensionFeature,
    private val securityService: SecurityService,
) : AbstractExtension(extensionFeature), UserMenuItemExtension {

    override val items: List<UserMenuItem>
        get() = listOfNotNull(
            UserMenuItem(
                groupId = CoreUserMenuGroups.SYSTEM,
                extension = feature,
                id = "status",
                name = "Audit trail status",
            ).takeIf {
                securityService.isGlobalFunctionGranted(GlobalSettings::class.java)
            }
        )
}
