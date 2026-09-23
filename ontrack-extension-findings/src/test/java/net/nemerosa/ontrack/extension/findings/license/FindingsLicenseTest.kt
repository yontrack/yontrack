package net.nemerosa.ontrack.extension.findings.license

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.findings.license.FindingsLicensedFeatureProvider.Companion.FEATURE_NATIVE_FORMATS
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FindingsLicenseTest {

    private val licenseControlService = mockk<LicenseControlService>()
    private val findingsLicense = FindingsLicense(licenseControlService)

    @Test
    fun `One licensed feature for every native format`() {
        assertEquals("extension.findings.native-formats", FEATURE_NATIVE_FORMATS)
        val feature = FindingsLicensedFeatureProvider().providedFeatures.single()
        assertEquals(FEATURE_NATIVE_FORMATS, feature.id)
        assertEquals("Native scanner formats", feature.name)
        assertFalse(feature.alwaysEnabled)
    }

    @Test
    fun `Native formats allowed by the licence`() {
        every { licenseControlService.isFeatureEnabled(FEATURE_NATIVE_FORMATS) } returns true
        assertTrue(findingsLicense.nativeFormatsEnabled)
        assertDoesNotThrow { findingsLicense.checkNativeFormat("sarif") }
    }

    @Test
    fun `Native formats not allowed by the licence are rejected, naming the feature`() {
        every { licenseControlService.isFeatureEnabled(FEATURE_NATIVE_FORMATS) } returns false
        assertFalse(findingsLicense.nativeFormatsEnabled)
        val ex = assertThrows<FindingsNativeFormatsLicenseException> {
            findingsLicense.checkNativeFormat("sarif")
        }
        assertEquals(
            "Findings report format `sarif` needs the licensed feature \"Native scanner formats\" " +
                    "(extension.findings.native-formats), which the current licence does not allow. " +
                    "The neutral format `findings` needs no licence.",
            ex.message
        )
    }

    @Test
    fun `The licence is read on every check, so that a lapsed licence stops native input at once`() {
        every { licenseControlService.isFeatureEnabled(FEATURE_NATIVE_FORMATS) } returns true andThen false
        assertDoesNotThrow { findingsLicense.checkNativeFormat("sarif") }
        assertThrows<FindingsNativeFormatsLicenseException> { findingsLicense.checkNativeFormat("sarif") }
    }
}
