package net.nemerosa.ontrack.extension.license

import net.nemerosa.ontrack.extension.license.signature.SignatureLicense
import net.nemerosa.ontrack.extension.license.signature.SignatureLicenseException
import net.nemerosa.ontrack.extension.license.signature.TestLicenseSigner
import net.nemerosa.ontrack.model.support.NameValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProductionLicenseServiceTest {

    @Test
    fun `No license content provided`() {
        val service = ProductionLicenseService(
            licenseConfigurationProperties = LicenseConfigurationProperties().apply {
                key = ""
            },
            licenseKeyPath = "/testing/keys/embedded.key"
        )
        assertFailsWith<SignatureLicenseException> {
            service.license
        }
    }

    @Test
    fun `License OK`() {
        val service = ProductionLicenseService(
            licenseConfigurationProperties = LicenseConfigurationProperties().apply {
                key =
                    "eyJkYXRhIjoiZXlKdVlXMWxJam9pV0V3aUxDSmhjM05wWjI1bFpTSTZJazVsYldWeWIzTmhJRXh2WTJGc0lpd2lkbUZzYVdSVmJuUnBiQ0k2Ym5Wc2JDd2liV0Y0VUhKdmFtVmpkSE1pT2pBc0ltWmxZWFIxY21WeklqcGJleUpwWkNJNkltVjRkR1Z1YzJsdmJpNWxiblpwY205dWJXVnVkSE1pTENKbGJtRmliR1ZrSWpwMGNuVmxMQ0prWVhSaElqcGJleUp1WVcxbElqb2liV0Y0Ulc1MmFYSnZibTFsYm5Seklpd2lkbUZzZFdVaU9pSXdJbjFkZlYxOSIsInNpZ25hdHVyZSI6Ik1FVUNJUURiNmd5WkZNbktINUxXdFhTODFtdFlwMzN2STFRSFNmNldTREdGd2UxUnBRSWdOekMwditTd29ad1pVZ01CcFlDeCtmb2g0L0EwSnVhd0hYU2V5UW1sQ3dvPSJ9"
            },
            licenseKeyPath = "/testing/keys/embedded.key"
        )
        val license = service.license
        assertEquals(
            license().toLicense("Production"),
            license
        )
        assertEquals(true, license.isFeatureEnabled("extension.environments"))
    }

    @Test
    fun `License with message OK`() {
        val service = ProductionLicenseService(
            licenseConfigurationProperties = LicenseConfigurationProperties().apply {
                key =
                    "eyJkYXRhIjoiZXlKdVlXMWxJam9pV0V3aUxDSmhjM05wWjI1bFpTSTZJazVsYldWeWIzTmhJRXh2WTJGc0lpd2lkbUZzYVdSVmJuUnBiQ0k2Ym5Wc2JDd2liV0Y0VUhKdmFtVmpkSE1pT2pBc0ltWmxZWFIxY21WeklqcGJleUpwWkNJNkltVjRkR1Z1YzJsdmJpNWxiblpwY205dWJXVnVkSE1pTENKbGJtRmliR1ZrSWpwMGNuVmxMQ0prWVhSaElqcGJleUp1WVcxbElqb2liV0Y0Ulc1MmFYSnZibTFsYm5Seklpd2lkbUZzZFdVaU9pSXdJbjFkZlYwc0ltMWxjM05oWjJVaU9pSlVhR2x6SUdseklHRWdkR1Z6ZENCc2FXTmxibk5sSW4wPSIsInNpZ25hdHVyZSI6Ik1FVUNJUURXM0pFTGJiK1paQnh1ZFRiQW5JZy9pbVFJR0lpUk5tMklOTCs5UktIMG1RSWdOOGg1STJrZ1cxR2tCZS92dnR5WUcxbnR1cDZ1bGJ5OTNtRnVYeTN5dHNRPSJ9"
            },
            licenseKeyPath = "/testing/keys/embedded.key"
        )
        val license = service.license
        assertEquals(
            license(message = "This is a test license").toLicense("Production"),
            license
        )
        assertEquals(true, license.isFeatureEnabled("extension.environments"))
    }

    @Test
    fun `A development key is refused by a production instance`() {
        val service = ProductionLicenseService(
            licenseConfigurationProperties = LicenseConfigurationProperties().apply {
                key = TestLicenseSigner.sign(license(usage = SignatureLicense.USAGE_DEV))
            },
            licenseKeyPath = TestLicenseSigner.PUBLIC_KEY_PATH
        )
        val ex = assertFailsWith<SignatureLicenseException> {
            service.license
        }
        assertEquals("A development key cannot be used by a production instance", ex.message)
    }

    @Test
    fun `A key of an unknown usage is refused by a production instance`() {
        val service = ProductionLicenseService(
            licenseConfigurationProperties = LicenseConfigurationProperties().apply {
                key = TestLicenseSigner.sign(license(usage = "other"))
            },
            licenseKeyPath = TestLicenseSigner.PUBLIC_KEY_PATH
        )
        val ex = assertFailsWith<SignatureLicenseException> {
            service.license
        }
        assertEquals("Unknown license usage: other", ex.message)
    }

    @Test
    fun `A key with no usage is a production key`() {
        val service = ProductionLicenseService(
            licenseConfigurationProperties = LicenseConfigurationProperties().apply {
                key = TestLicenseSigner.sign(license())
            },
            licenseKeyPath = TestLicenseSigner.PUBLIC_KEY_PATH
        )
        assertEquals(
            license().toLicense("Production"),
            service.license
        )
    }

    private fun license(
        message: String? = null,
        usage: String? = null,
    ) = SignatureLicense(
        name = "XL",
        assignee = "Nemerosa Local",
        validUntil = null,
        maxProjects = 0,
        features = listOf(
            LicenseFeatureData(
                id = "extension.environments",
                enabled = true,
                data = listOf(
                    NameValue("maxEnvironments", "0")
                ),
            )
        ),
        message = message,
        usage = usage,
    )

}