package net.nemerosa.ontrack.extension.license

import net.nemerosa.ontrack.common.BaseException
import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.model.exceptions.NotFoundException
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Licence of the development profile: the licensed features its development key grants (see
 * [DevLicenseKeyService]), unless one has been disabled through [setFeatureEnabled]. Without a
 * valid development key, no licensed feature is enabled.
 *
 * Disabling a feature is how a test running against a development instance — the KDSL
 * acceptance tests, the Playwright tests — checks the behaviour without it. The state is held in
 * memory only, and is lost at restart.
 */
@Component
@Profile(RunProfile.DEV)
class DevLicenseService(
    licensedFeatureProviders: List<LicensedFeatureProvider>,
    private val devLicenseKeyService: DevLicenseKeyService,
) : LicenseService {

    private val featureIds: Set<String> = licensedFeatureProviders
        .flatMap { it.providedFeatures }
        .map { it.id }
        .toSet()

    private val disabledFeatures: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override val license: License
        get() {
            val keyLicense = devLicenseKeyService.keyLicense
            return License(
                type = "dev",
                name = keyLicense?.name ?: "Development license",
                assignee = keyLicense?.assignee ?: "Development",
                maxProjects = keyLicense?.maxProjects ?: 0,
                active = true,
                validUntil = keyLicense?.validUntil,
                features = featureIds.map {
                    val granted = keyLicense?.findFeatureData(it)
                    LicenseFeatureData(
                        id = it,
                        enabled = granted?.enabled == true && it !in disabledFeatures,
                        data = granted?.data ?: emptyList(),
                    )
                },
                message = if (keyLicense != null) MESSAGE else MESSAGE_NO_KEY,
            )
        }

    /**
     * Enables or disables a licensed feature.
     *
     * @param featureId ID of the feature
     * @param enabled Whether the feature is enabled
     * @throws DevLicenseFeatureNotFoundException When no such feature is provided
     * @throws DevLicenseFeatureNotGrantedException When enabling a feature the development key does not grant
     */
    fun setFeatureEnabled(featureId: String, enabled: Boolean) {
        if (featureId !in featureIds) {
            throw DevLicenseFeatureNotFoundException(featureId)
        }
        if (enabled) {
            if (devLicenseKeyService.keyLicense?.isFeatureEnabled(featureId) != true) {
                throw DevLicenseFeatureNotGrantedException(featureId)
            }
            disabledFeatures -= featureId
        } else {
            disabledFeatures += featureId
        }
    }

    companion object {
        /**
         * Message of a development instance with a development key
         */
        const val MESSAGE = "You're currently using a development license."

        /**
         * Message of a development instance without a development key
         */
        const val MESSAGE_NO_KEY = "Development license without a key: licensed features are disabled."
    }
}

/**
 * Enabling or disabling a feature which is not provided.
 */
class DevLicenseFeatureNotFoundException(featureId: String) :
    NotFoundException("Licensed feature not found: %s", featureId)

/**
 * Enabling a feature which the development key does not grant.
 */
class DevLicenseFeatureNotGrantedException(featureId: String) :
    BaseException("The development key does not grant the licensed feature: $featureId")
