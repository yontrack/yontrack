package net.nemerosa.ontrack.extension.agents.license

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.agents.license.AgentsLicensedFeatureProvider.Companion.FEATURE_AGENTS
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentsLicenseTest {

    private val licenseControlService = mockk<LicenseControlService>()
    private val agentsLicense = AgentsLicense(licenseControlService)

    @Test
    fun `One boolean licensed feature for the agent governance`() {
        assertEquals("extension.agents", FEATURE_AGENTS)
        val feature = AgentsLicensedFeatureProvider().providedFeatures.single()
        assertEquals(FEATURE_AGENTS, feature.id)
        assertEquals("Agent governance", feature.name)
        assertFalse(feature.alwaysEnabled)
    }

    @Test
    fun `The licence is read on every call, so that a lapsed licence stops the rulings at once`() {
        every { licenseControlService.isFeatureEnabled(FEATURE_AGENTS) } returns true andThen false
        assertTrue(agentsLicense.agentsEnabled)
        assertFalse(agentsLicense.agentsEnabled)
    }
}
