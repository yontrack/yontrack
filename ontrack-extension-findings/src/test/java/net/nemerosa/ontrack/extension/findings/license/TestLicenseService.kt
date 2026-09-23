package net.nemerosa.ontrack.extension.findings.license

import net.nemerosa.ontrack.extension.license.License
import net.nemerosa.ontrack.extension.license.LicenseService
import java.util.concurrent.ConcurrentHashMap

/**
 * Licence of the integration tests: the development licence, all features enabled, except those
 * a test disables for the time of a block — how a test runs "without the feature" in the same
 * Spring context as the others.
 *
 * @param delegate Licence to start from
 */
class TestLicenseService(
    private val delegate: LicenseService,
) : LicenseService {

    private val disabledFeatures: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override val license: License
        get() = delegate.license.let { license ->
            license.copy(
                features = license.features.map { feature ->
                    if (feature.id in disabledFeatures) feature.copy(enabled = false) else feature
                }
            )
        }

    /**
     * Runs [code] with the given feature disabled by the licence.
     */
    fun <T> withoutFeature(featureId: String, code: () -> T): T {
        disabledFeatures += featureId
        return try {
            code()
        } finally {
            disabledFeatures -= featureId
        }
    }
}
