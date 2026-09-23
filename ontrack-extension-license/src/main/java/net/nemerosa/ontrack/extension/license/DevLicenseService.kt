package net.nemerosa.ontrack.extension.license

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.model.exceptions.NotFoundException
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Licence of the development profile: every licensed feature is enabled, unless it has been
 * disabled through [setFeatureEnabled].
 *
 * Disabling a feature is how a test running against a development instance — the KDSL
 * acceptance tests, the Playwright tests — checks the behaviour without it. The state is held in
 * memory only, and is lost at restart.
 */
@Component
@Profile(RunProfile.DEV)
class DevLicenseService(
    licensedFeatureProviders: List<LicensedFeatureProvider>,
) : LicenseService {

    private val featureIds: Set<String> = licensedFeatureProviders
        .flatMap { it.providedFeatures }
        .map { it.id }
        .toSet()

    private val disabledFeatures: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override val license: License
        get() = License(
            type = "dev",
            name = "Development license",
            assignee = "Development",
            maxProjects = 0,
            active = true,
            validUntil = null,
            features = featureIds.map {
                LicenseFeatureData(
                    id = it,
                    enabled = it !in disabledFeatures,
                    data = emptyList(),
                )
            },
            message = "You're currently using a development license."
        )

    /**
     * Enables or disables a licensed feature.
     *
     * @param featureId ID of the feature
     * @param enabled Whether the feature is enabled
     * @throws DevLicenseFeatureNotFoundException When no such feature is provided
     */
    fun setFeatureEnabled(featureId: String, enabled: Boolean) {
        if (featureId !in featureIds) {
            throw DevLicenseFeatureNotFoundException(featureId)
        }
        if (enabled) {
            disabledFeatures -= featureId
        } else {
            disabledFeatures += featureId
        }
    }
}

/**
 * Enabling or disabling a feature which is not provided.
 */
class DevLicenseFeatureNotFoundException(featureId: String) :
    NotFoundException("Licensed feature not found: %s", featureId)
