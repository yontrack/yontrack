package net.nemerosa.ontrack.extension.license

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.license.signature.SignatureLicense
import net.nemerosa.ontrack.extension.license.signature.SignatureLicenseException
import net.nemerosa.ontrack.extension.license.signature.decodeSignatureLicense
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Development key of the `dev` profile: a licence key, in `ontrack.config.license.key` like any
 * other, signed by the same key pair as the production keys, and whose usage is
 * [SignatureLicense.USAGE_DEV].
 *
 * Only the Yontrack development pipelines hold one, so that `SPRING_PROFILES_ACTIVE=dev` on a
 * released image does not unlock the licensed features. With no key, or a key which is not a valid
 * development key, the instance still starts, with no licensed feature.
 */
@Component
@Profile(RunProfile.DEV)
class SignedDevLicenseKeyService(
    private val licenseConfigurationProperties: LicenseConfigurationProperties,
    private val licenseKeyPath: String = "/keys/embedded.key",
) : DevLicenseKeyService {

    private val logger: Logger = LoggerFactory.getLogger(SignedDevLicenseKeyService::class.java)

    override val keyLicense: License? by lazy {
        val key = licenseConfigurationProperties.key
        if (key.isBlank()) {
            logger.warn("[license] No development key: the licensed features are disabled")
            null
        } else {
            try {
                val signatureLicense = decodeSignatureLicense(key, licenseKeyPath)
                if (signatureLicense.usage == SignatureLicense.USAGE_DEV) {
                    signatureLicense.toLicense("Development")
                } else {
                    logger.warn("[license] The key is not a development key: the licensed features are disabled")
                    null
                }
            } catch (any: SignatureLicenseException) {
                logger.warn("[license] The development key is not valid (${any.message}): the licensed features are disabled")
                null
            } catch (any: Exception) {
                logger.warn("[license] The development key cannot be read (${any.message}): the licensed features are disabled")
                null
            }
        }
    }
}
