package net.nemerosa.ontrack.extension.license

import net.nemerosa.ontrack.extension.license.signature.SignatureLicense
import net.nemerosa.ontrack.extension.license.signature.TestLicenseSigner
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignedDevLicenseKeyServiceTest {

    @Test
    fun `No key grants nothing`() {
        assertNull(service(key = "").keyLicense)
    }

    @Test
    fun `A development key grants its features`() {
        val license = service(key = TestLicenseSigner.sign(devLicense())).keyLicense
        assertNotNull(license) {
            assertEquals("Development", it.name)
            assertEquals(LocalDate.of(2027, 10, 22), it.validUntil?.toLocalDate())
            assertEquals(0, it.maxProjects)
            assertTrue(it.isFeatureEnabled(LicenseFixtures.sampleFeatureId))
        }
    }

    @Test
    fun `A production key grants nothing to a development instance`() {
        assertNull(service(key = TestLicenseSigner.sign(devLicense(usage = null))).keyLicense)
    }

    @Test
    fun `A key which cannot be read grants nothing`() {
        assertNull(service(key = "not-a-key").keyLicense)
    }

    @Test
    fun `A key signed by another key pair grants nothing`() {
        assertNull(service(key = LicenseKeysFixtures.UNLIMITED).keyLicense)
    }

    private fun service(key: String) = SignedDevLicenseKeyService(
        licenseConfigurationProperties = LicenseConfigurationProperties().apply {
            this.key = key
        },
        licenseKeyPath = TestLicenseSigner.PUBLIC_KEY_PATH,
    )

    private fun devLicense(
        usage: String? = SignatureLicense.USAGE_DEV,
    ) = SignatureLicense(
        name = "Development",
        assignee = "Yontrack development pipelines",
        validUntil = LocalDate.of(2027, 10, 22),
        maxProjects = 0,
        features = listOf(
            LicenseFeatureData(
                id = LicenseFixtures.sampleFeatureId,
                enabled = true,
                data = emptyList(),
            )
        ),
        usage = usage,
    )
}
