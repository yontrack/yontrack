package net.nemerosa.ontrack.extension.findings.license

import net.nemerosa.ontrack.extension.license.LicensedFeatureProvider
import net.nemerosa.ontrack.extension.license.ProvidedLicensedFeature
import org.springframework.stereotype.Component

/**
 * The licensed feature of the findings: one for every native scanner format — SARIF, Trivy JSON,
 * any future one.
 *
 * The core of the findings is not licensed: the model, the neutral format, the reading surfaces,
 * the events. Without the licence, findings work fully for anyone who writes a converter to the
 * neutral format; the licence buys not having to.
 */
@Component
class FindingsLicensedFeatureProvider : LicensedFeatureProvider {

    override val providedFeatures: List<ProvidedLicensedFeature> = listOf(
        ProvidedLicensedFeature(
            id = FEATURE_NATIVE_FORMATS,
            name = FEATURE_NATIVE_FORMATS_NAME,
        )
    )

    companion object {
        /**
         * ID of the licensed feature for the native scanner formats
         */
        const val FEATURE_NATIVE_FORMATS = "extension.findings.native-formats"

        /**
         * Display name of the licensed feature for the native scanner formats
         */
        const val FEATURE_NATIVE_FORMATS_NAME = "Native scanner formats"
    }
}
