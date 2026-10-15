package net.nemerosa.ontrack.extension.license

import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The evaluation key shipped as the default of `compose/docker-compose.yml` must be signed with the
 * production key and still be valid for a while: this test fails 90 days before the key expires, so that
 * a new one is issued before a fresh `docker compose up` starts with an expired licence (#2095).
 *
 * It runs in the `composeEvaluationLicenseKeyTest` task, whose inputs are the compose file and the day,
 * so that the build cache never skips it.
 */
class ComposeEvaluationLicenseKeyTest {

    @Test
    fun `Evaluation license key of the local Docker Compose`() {
        val composeFile = File(
            System.getProperty("ontrack.test.compose-file") ?: "../compose/docker-compose.yml"
        )
        val key = Regex("""\$\{ONTRACK_CONFIG_LICENSE_KEY:-([^}]+)}""")
            .find(composeFile.readText())
            ?.groupValues?.get(1)
            ?: fail("No default ONTRACK_CONFIG_LICENSE_KEY in $composeFile")
        val service = ProductionLicenseService(
            licenseConfigurationProperties = LicenseConfigurationProperties().apply {
                this.key = key
            },
        )
        val license = service.license
        assertEquals(false, license.isFeatureEnabled("extension.environments"))
        assertEquals(10, license.maxProjects)
        val validUntil = assertNotNull(license.validUntil, "The evaluation key must have an expiry date")
        assertTrue(
            validUntil.isAfter(LocalDateTime.now().plusDays(90)),
            "The evaluation key of compose/docker-compose.yml expires on $validUntil: " +
                    "issue a new one from yontrack/yontrack-license"
        )
    }

}
