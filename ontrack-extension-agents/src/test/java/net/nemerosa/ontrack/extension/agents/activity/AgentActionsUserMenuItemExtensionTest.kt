package net.nemerosa.ontrack.extension.agents.activity

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.agents.AgentsExtensionFeature
import net.nemerosa.ontrack.extension.agents.license.AgentsLicense
import net.nemerosa.ontrack.extension.agents.license.AgentsLicensedFeatureProvider.Companion.FEATURE_AGENTS
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AgentActionsUserMenuItemExtensionTest {

    private lateinit var licenseControlService: LicenseControlService
    private lateinit var extension: AgentActionsUserMenuItemExtension

    @BeforeEach
    fun init() {
        val feature = mockk<AgentsExtensionFeature>()
        every { feature.id } returns "agents"
        licenseControlService = mockk()
        extension = AgentActionsUserMenuItemExtension(
            agentsExtensionFeature = feature,
            agentsLicense = AgentsLicense(licenseControlService),
        )
    }

    @Test
    fun `Latest agent actions in the information group with the licence, for every user`() {
        every { licenseControlService.isFeatureEnabled(FEATURE_AGENTS) } returns true
        assertEquals(
            listOf(
                UserMenuItem(
                    groupId = CoreUserMenuGroups.INFORMATION,
                    extension = "extension/agents",
                    id = "actions",
                    name = "Latest agent actions",
                )
            ),
            extension.items,
        )
    }

    @Test
    fun `No latest agent actions without the licence`() {
        every { licenseControlService.isFeatureEnabled(FEATURE_AGENTS) } returns false
        assertEquals(emptyList(), extension.items)
    }
}
