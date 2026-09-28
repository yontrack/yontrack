package net.nemerosa.ontrack.extension.scorecard.license

import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import net.nemerosa.ontrack.extension.license.control.LicenseFeatureException
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicensedFeatureProvider.Companion.FEATURE_SCORECARD
import org.springframework.stereotype.Component

/**
 * Checks of the licence for the estates.
 *
 * The schema never changes with the licence: without it, the estate queries, fields and mutations
 * raise a licence error, the daily jobs skip the estates, and the readings of the estates are not
 * shown on the scorecards. The stored estates and their snapshots are kept, and come back with the
 * licence.
 */
@Component
class ScorecardLicense(
    private val licenseControlService: LicenseControlService,
) {

    /**
     * Whether the licence allows the estates. Read on every call, never cached: a licence which
     * lapses stops the estates at once.
     */
    val estatesEnabled: Boolean
        get() = licenseControlService.isFeatureEnabled(FEATURE_SCORECARD)

    /**
     * Checks that the licence allows the estates.
     *
     * @throws LicenseFeatureException When it does not
     */
    fun checkEstates() {
        if (!estatesEnabled) {
            throw LicenseFeatureException(FEATURE_SCORECARD)
        }
    }
}
