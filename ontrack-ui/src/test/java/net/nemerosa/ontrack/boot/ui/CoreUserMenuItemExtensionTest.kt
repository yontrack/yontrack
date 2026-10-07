package net.nemerosa.ontrack.boot.ui

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.model.security.EventsAudit
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreUserMenuItemExtensionTest {

    private lateinit var securityService: SecurityService
    private lateinit var extension: CoreUserMenuItemExtension

    private val eventsItem = UserMenuItem(
        groupId = CoreUserMenuGroups.INFORMATION,
        extension = "core/admin",
        id = "events",
        name = "Events",
    )

    @BeforeEach
    fun init() {
        securityService = mockk()
        every { securityService.isGlobalFunctionGranted(any()) } returns false
        extension = CoreUserMenuItemExtension(securityService)
    }

    @Test
    fun `Events in the information group for the holders of the events audit function`() {
        every { securityService.isGlobalFunctionGranted(EventsAudit::class.java) } returns true
        assertTrue(eventsItem in extension.items, "Events item is in the menu")
    }

    @Test
    fun `No events without the events audit function`() {
        assertFalse(extension.items.any { it.id == "events" }, "Events item is not in the menu")
    }
}
