package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.scorecard.model.EstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingSet
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityCoverage
import net.nemerosa.ontrack.extension.scorecard.settings.ScorecardSettings
import net.nemerosa.ontrack.model.settings.CachedSettingsService
import org.springframework.stereotype.Component

/**
 * What covered means for the security readings of a set: the kinds of scan its estate expects,
 * none with no estate, and the freshness of its estate, else the one of the settings.
 */
@Component
class SecurityCoverages(
    private val cachedSettingsService: CachedSettingsService,
) {

    fun coverage(set: ReadingSet): SecurityCoverage {
        val security = when (set) {
            NoEstateReadingSet -> null
            is EstateReadingSet -> set.estate.security
        }
        return SecurityCoverage(
            expectedKinds = security?.expectedKinds?.toSet() ?: emptySet(),
            freshnessDays = security?.freshnessDays
                ?: cachedSettingsService.getCachedSettings(ScorecardSettings::class.java).securityFreshnessDays,
        )
    }
}
