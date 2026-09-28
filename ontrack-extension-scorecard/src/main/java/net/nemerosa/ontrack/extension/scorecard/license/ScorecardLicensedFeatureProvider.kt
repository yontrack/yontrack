package net.nemerosa.ontrack.extension.scorecard.license

import net.nemerosa.ontrack.extension.license.LicensedFeatureProvider
import net.nemerosa.ontrack.extension.license.ProvidedLicensedFeature
import org.springframework.stereotype.Component

/**
 * The licensed feature of the delivery scorecard: the estates, and the readings of the projects
 * in them.
 *
 * The readings of a project with no estate are not licensed.
 */
@Component
class ScorecardLicensedFeatureProvider : LicensedFeatureProvider {

    override val providedFeatures: List<ProvidedLicensedFeature> = listOf(
        ProvidedLicensedFeature(
            id = FEATURE_SCORECARD,
            name = FEATURE_SCORECARD_NAME,
        )
    )

    companion object {
        /**
         * ID of the licensed feature of the delivery scorecard
         */
        const val FEATURE_SCORECARD = "extension.scorecard"

        /**
         * Display name of the licensed feature of the delivery scorecard
         */
        const val FEATURE_SCORECARD_NAME = "Delivery scorecard"
    }
}
