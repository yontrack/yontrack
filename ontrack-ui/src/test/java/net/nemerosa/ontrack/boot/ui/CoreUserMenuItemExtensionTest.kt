package net.nemerosa.ontrack.boot.ui

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.model.security.Account
import net.nemerosa.ontrack.model.security.AccountKind
import net.nemerosa.ontrack.model.security.AccountManagement
import net.nemerosa.ontrack.model.security.AuthenticatedUser
import net.nemerosa.ontrack.model.security.EventsAudit
import net.nemerosa.ontrack.model.security.SecurityRole
import net.nemerosa.ontrack.model.structure.ID
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

    private val myAgentsItem = UserMenuItem(
        groupId = CoreUserMenuGroups.USER,
        extension = "core/admin",
        id = "my-agents",
        name = "My agents",
    )

    private val agentsItem = UserMenuItem(
        groupId = CoreUserMenuGroups.SYSTEM,
        extension = "core/admin",
        id = "agents",
        name = "Agents",
    )

    @BeforeEach
    fun init() {
        securityService = mockk()
        every { securityService.isGlobalFunctionGranted(any()) } returns false
        every { securityService.currentUser } returns null
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

    @Test
    fun `My agents for every person`() {
        assertTrue(myAgentsItem in extension.items, "My agents item is in the menu")
    }

    @Test
    fun `No my agents for an agent`() {
        val user = mockk<AuthenticatedUser>()
        every { user.account } returns Account(
            id = ID.of(1),
            fullName = "Agent",
            email = "agent[agent]",
            role = SecurityRole.USER,
            kind = AccountKind.AGENT,
        )
        every { securityService.currentUser } returns user
        assertFalse(extension.items.any { it.id == "my-agents" }, "My agents item is not in the menu")
    }

    @Test
    fun `Agents in the system group for the account managers`() {
        every { securityService.isGlobalFunctionGranted(AccountManagement::class.java) } returns true
        assertTrue(agentsItem in extension.items, "Agents item is in the menu")
    }

    @Test
    fun `No agents without the account management function`() {
        assertFalse(extension.items.any { it.id == "agents" }, "Agents item is not in the menu")
    }
}
