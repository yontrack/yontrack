package net.nemerosa.ontrack.extension.scorecard.ui

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import net.nemerosa.ontrack.extension.scorecard.ScorecardExtensionFeature
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicense
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicensedFeatureProvider.Companion.FEATURE_SCORECARD
import net.nemerosa.ontrack.model.support.CoreUserMenuGroups
import net.nemerosa.ontrack.model.support.UserMenuItem
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ScorecardsUserMenuItemExtensionTest {

    private lateinit var licenseControlService: LicenseControlService
    private lateinit var extension: ScorecardsUserMenuItemExtension

    @BeforeEach
    fun init() {
        val feature = mockk<ScorecardExtensionFeature>()
        every { feature.id } returns "scorecard"
        licenseControlService = mockk()
        extension = ScorecardsUserMenuItemExtension(
            scorecardExtensionFeature = feature,
            scorecardLicense = ScorecardLicense(licenseControlService),
        )
    }

    @Test
    fun `Scorecards in the information group with the licence, for every user`() {
        every { licenseControlService.isFeatureEnabled(FEATURE_SCORECARD) } returns true
        assertEquals(
            listOf(
                UserMenuItem(
                    groupId = CoreUserMenuGroups.INFORMATION,
                    extension = "extension/scorecard",
                    id = "scorecards",
                    name = "Scorecards",
                )
            ),
            extension.items,
        )
    }

    @Test
    fun `No scorecards without the licence`() {
        every { licenseControlService.isFeatureEnabled(FEATURE_SCORECARD) } returns false
        assertEquals(emptyList(), extension.items)
    }
}
