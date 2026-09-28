package net.nemerosa.ontrack.extension.scorecard.ui

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import net.nemerosa.ontrack.extension.scorecard.ScorecardExtensionFeature
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicense
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicensedFeatureProvider.Companion.FEATURE_SCORECARD
import net.nemerosa.ontrack.extension.scorecard.security.EstateManagement
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EstatesUserMenuItemExtensionTest {

    private lateinit var licenseControlService: LicenseControlService
    private lateinit var securityService: SecurityService
    private lateinit var extension: EstatesUserMenuItemExtension

    @BeforeEach
    fun init() {
        val feature = mockk<ScorecardExtensionFeature>()
        every { feature.id } returns "scorecard"
        licenseControlService = mockk()
        securityService = mockk()
        extension = EstatesUserMenuItemExtension(
            scorecardExtensionFeature = feature,
            scorecardLicense = ScorecardLicense(licenseControlService),
            securityService = securityService,
        )
    }

    private fun given(licensed: Boolean, granted: Boolean) {
        every { licenseControlService.isFeatureEnabled(FEATURE_SCORECARD) } returns licensed
        every { securityService.isGlobalFunctionGranted(EstateManagement::class.java) } returns granted
    }

    @Test
    fun `Estates in the configurations group with the licence and the estate management`() {
        given(licensed = true, granted = true)
        assertEquals(
            listOf(
                UserMenuItem(
                    groupId = CoreUserMenuGroups.CONFIGURATIONS,
                    extension = "extension/scorecard",
                    id = "estates",
                    name = "Estates",
                )
            ),
            extension.items,
        )
    }

    @Test
    fun `No estates without the estate management`() {
        given(licensed = true, granted = false)
        assertEquals(emptyList(), extension.items)
    }

    @Test
    fun `No estates without the licence`() {
        given(licensed = false, granted = true)
        assertEquals(emptyList(), extension.items)
    }
}
