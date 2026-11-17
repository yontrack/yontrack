package net.nemerosa.ontrack.extension.notifications.http.config

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.notifications.NotificationsExtensionFeature
import net.nemerosa.ontrack.model.security.GlobalSettings
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HttpConfigurationUserMenuItemExtensionTest {

    private val securityService = mockk<SecurityService>()

    private val extension = HttpConfigurationUserMenuItemExtension(
        notificationsExtensionFeature = mockk<NotificationsExtensionFeature> {
            every { id } returns "notifications"
        },
        securityService = securityService,
    )

    @Test
    fun `No menu entry without the global settings function`() {
        every { securityService.isGlobalFunctionGranted(GlobalSettings::class.java) } returns false
        assertEquals(emptyList(), extension.items)
    }

    @Test
    fun `Menu entry in the configurations group`() {
        every { securityService.isGlobalFunctionGranted(GlobalSettings::class.java) } returns true
        assertEquals(
            listOf(
                UserMenuItem(
                    groupId = CoreUserMenuGroups.CONFIGURATIONS,
                    extension = "extension/notifications",
                    id = "http/configurations",
                    name = "HTTP configurations",
                )
            ),
            extension.items,
        )
    }
}
