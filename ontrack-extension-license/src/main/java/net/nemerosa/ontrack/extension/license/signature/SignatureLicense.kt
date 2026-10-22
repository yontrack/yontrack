package net.nemerosa.ontrack.extension.license.signature

import net.nemerosa.ontrack.extension.license.License
import net.nemerosa.ontrack.extension.license.LicenseFeatureData
import java.time.LocalDate

/**
 * Signed content of a licence key.
 *
 * @property usage Where the key may be used: null for a production instance, [USAGE_DEV] for an
 * instance running the `dev` profile. A production instance refuses a development key, and a
 * development instance grants nothing from a production one.
 */
data class SignatureLicense(
    val name: String,
    val assignee: String,
    val validUntil: LocalDate?,
    val maxProjects: Int,
    val features: List<LicenseFeatureData>,
    val message: String? = null,
    val usage: String? = null,
) {
    fun toLicense(type: String) =
        License(
            type = type,
            name = name,
            assignee = assignee,
            validUntil = validUntil?.atStartOfDay()?.plusHours(23),
            active = true,
            maxProjects = maxProjects,
            features = features,
            message = message,
        )

    companion object {
        /**
         * Usage of a development key
         */
        const val USAGE_DEV = "dev"
    }
}
