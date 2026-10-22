package net.nemerosa.ontrack.extension.license.ui

import net.nemerosa.ontrack.common.RunProfile
import net.nemerosa.ontrack.extension.license.DevLicenseFeatureNotGrantedException
import net.nemerosa.ontrack.extension.license.DevLicenseService
import net.nemerosa.ontrack.model.security.ApplicationManagement
import net.nemerosa.ontrack.model.security.SecurityService
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Enabling and disabling the features of the development licence, so that a test running against
 * a development instance can check a behaviour without a licensed feature. Only the features its
 * development key grants can be enabled.
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

    /**
     * Enabling a feature the development key does not grant is forbidden, and says which feature.
     */
    @ExceptionHandler(DevLicenseFeatureNotGrantedException::class)
    fun onFeatureNotGranted(ex: DevLicenseFeatureNotGrantedException): ResponseEntity<DevLicenseErrorMessage> =
        ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(
                DevLicenseErrorMessage(
                    status = HttpStatus.FORBIDDEN.value(),
                    message = ex.message ?: "",
                )
            )
}

/**
 * Error answered by [DevLicenseController], in the shape of the other errors of the API.
 */
data class DevLicenseErrorMessage(
    val status: Int,
    val message: String,
)

/**
 * Whether a feature of the development licence is enabled.
 */
data class DevLicenseFeatureInput(
    val enabled: Boolean,
)
