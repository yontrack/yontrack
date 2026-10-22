package net.nemerosa.ontrack.extension.license

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.license.signature.AbstractSignatureLicenseService
import net.nemerosa.ontrack.extension.license.signature.SignatureLicense
import net.nemerosa.ontrack.extension.license.signature.SignatureLicenseException
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile(RunProfile.PROD)
class ProductionLicenseService(
    licenseConfigurationProperties: LicenseConfigurationProperties,
    licenseKeyPath: String = "/keys/embedded.key",
) : AbstractSignatureLicenseService(licenseKeyPath) {

    override val licenseType: String = "Production"
    override val encodedLicense: String? = licenseConfigurationProperties.key

    /**
     * Only a production key - one with no usage - runs a production instance.
     */
    override fun checkSignatureLicense(signatureLicense: SignatureLicense) {
        when (val usage = signatureLicense.usage) {
            null -> {}
            SignatureLicense.USAGE_DEV ->
                throw SignatureLicenseException("A development key cannot be used by a production instance")

            else -> throw SignatureLicenseException("Unknown license usage: $usage")
        }
    }
}
