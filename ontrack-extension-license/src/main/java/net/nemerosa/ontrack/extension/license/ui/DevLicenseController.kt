package net.nemerosa.ontrack.extension.license.ui

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.license.DevLicenseService
import net.nemerosa.ontrack.model.security.ApplicationManagement
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Enabling and disabling the features of the development licence, so that a test running against
 * a development instance can check a behaviour without a licensed feature.
 *
 * Development profile only: a production instance has no such endpoint.
 */
@Profile(RunProfile.DEV)
@RestController
@RequestMapping("/extension/license/dev")
class DevLicenseController(
    private val devLicenseService: DevLicenseService,
    private val securityService: SecurityService,
) {

    @PutMapping("features/{featureId}")
    fun setFeatureEnabled(
        @PathVariable featureId: String,
        @RequestBody input: DevLicenseFeatureInput,
    ) {
        securityService.checkGlobalFunction(ApplicationManagement::class.java)
        devLicenseService.setFeatureEnabled(featureId, input.enabled)
    }
}

/**
 * Whether a feature of the development licence is enabled.
 */
data class DevLicenseFeatureInput(
    val enabled: Boolean,
)
