package net.nemerosa.ontrack.extension.findings.license

import net.nemerosa.ontrack.common.UserException
import net.nemerosa.ontrack.extension.findings.license.FindingsLicensedFeatureProvider.Companion.FEATURE_NATIVE_FORMATS
import net.nemerosa.ontrack.extension.findings.license.FindingsLicensedFeatureProvider.Companion.FEATURE_NATIVE_FORMATS_NAME
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import org.springframework.stereotype.Component

/**
 * Checks of the licence for the findings.
 *
 * Only new native input is checked: a lapsed licence never hides data, and stored findings keep
 * resolving through the neutral format.
 */
@Component
class FindingsLicense(
    private val licenseControlService: LicenseControlService,
) {

    /**
     * Whether the licence allows the native scanner formats. Read on every call, never cached: a
     * licence which lapses stops native input at once.
     */
    val nativeFormatsEnabled: Boolean
        get() = licenseControlService.isFeatureEnabled(FEATURE_NATIVE_FORMATS)

    /**
     * Checks that the licence allows posting a report in a native format.
     *
     * @param format Native format of the report
     * @throws FindingsNativeFormatsLicenseException When it does not
     */
    fun checkNativeFormat(format: String) {
        if (!nativeFormatsEnabled) {
            throw FindingsNativeFormatsLicenseException(format)
        }
    }
}

/**
 * A report in a native format, posted without the licensed feature for it. Nothing is created: the
 * CI step posting it fails, with a message naming the feature.
 */
class FindingsNativeFormatsLicenseException(format: String) : UserException(
    "Findings report format `$format` needs the licensed feature \"$FEATURE_NATIVE_FORMATS_NAME\" " +
            "($FEATURE_NATIVE_FORMATS), which the current licence does not allow. " +
            "The neutral format `findings` needs no licence."
)
