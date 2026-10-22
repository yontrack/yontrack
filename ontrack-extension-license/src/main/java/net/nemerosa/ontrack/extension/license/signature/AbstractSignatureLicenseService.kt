package net.nemerosa.ontrack.extension.license.signature

import net.nemerosa.ontrack.extension.license.License
import net.nemerosa.ontrack.extension.license.LicenseService

abstract class AbstractSignatureLicenseService(
    private val licenseKeyPath: String,
) : LicenseService {

    abstract val encodedLicense: String?
    abstract val licenseType: String

    /**
     * Checks that the signed licence may be used by this instance.
     *
     * @throws SignatureLicenseException When it may not
     */
    protected open fun checkSignatureLicense(signatureLicense: SignatureLicense) {}

    override val license: License by lazy {
        val signatureLicense = decodeSignatureLicense(encodedLicense, licenseKeyPath)
        checkSignatureLicense(signatureLicense)
        signatureLicense.toLicense(licenseType)
    }

}
