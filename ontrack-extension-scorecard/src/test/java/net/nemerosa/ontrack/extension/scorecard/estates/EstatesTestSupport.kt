package net.nemerosa.ontrack.extension.scorecard.estates

import net.nemerosa.ontrack.extension.license.DevLicenseService
import net.nemerosa.ontrack.extension.scorecard.license.ScorecardLicensedFeatureProvider
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.labels.Label
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.beans.factory.annotation.Autowired

/**
 * Support for the integration tests of the estates.
 */
abstract class EstatesTestSupport : AbstractQLKTITSupport() {

    @Autowired
    protected lateinit var estateService: EstateService

    @Autowired
    private lateinit var devLicenseService: DevLicenseService

    /**
     * Runs [code] without the licensed feature of the delivery scorecard.
     */
    protected fun <T> withoutScorecardLicence(code: () -> T): T {
        devLicenseService.setFeatureEnabled(ScorecardLicensedFeatureProvider.FEATURE_SCORECARD, false)
        return try {
            code()
        } finally {
            devLicenseService.setFeatureEnabled(ScorecardLicensedFeatureProvider.FEATURE_SCORECARD, true)
        }
    }

    /**
     * Creates an estate, as admin.
     */
    protected fun estate(
        vararg labels: Label,
        name: String = uid("E"),
        marker: EstateMarker? = null,
        readingConfigs: List<EstateReadingConfig> = emptyList(),
        security: EstateSecurity = EstateSecurity(),
    ): Estate = asAdmin {
        estateService.create(
            EstateInput(
                name = name,
                description = null,
                labels = labels.map { it.getDisplay() },
                marker = marker,
                readingConfigs = readingConfigs,
                security = security,
            )
        )
    }
}
