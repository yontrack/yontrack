package net.nemerosa.ontrack.extension.audittrail.ui

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.audittrail.AuditTrailExtensionFeature
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AuditTrailStatusUserMenuItemExtensionTest {

    private val securityService = mockk<SecurityService>()
    private val extension = AuditTrailStatusUserMenuItemExtension(
        extensionFeature = mockk<AuditTrailExtensionFeature> {
            every { id } returns "audit-trail"
        },
        securityService = securityService,
    )

    @Test
    fun `Audit trail status in the system group, with the global settings`() {
        every { securityService.isGlobalFunctionGranted(GlobalSettings::class.java) } returns true
        assertEquals(
            listOf(
                UserMenuItem(
                    groupId = CoreUserMenuGroups.SYSTEM,
                    extension = "extension/audit-trail",
                    id = "status",
                    name = "Audit trail status",
                )
            ),
            extension.items
        )
    }

    @Test
    fun `No audit trail status without the global settings`() {
        every { securityService.isGlobalFunctionGranted(GlobalSettings::class.java) } returns false
        assertEquals(emptyList(), extension.items)
    }
}
