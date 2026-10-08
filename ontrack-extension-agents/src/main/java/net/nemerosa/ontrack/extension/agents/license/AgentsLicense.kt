package net.nemerosa.ontrack.extension.agents.license

import net.nemerosa.ontrack.extension.agents.license.AgentsLicensedFeatureProvider.Companion.FEATURE_AGENTS
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import org.springframework.stereotype.Component

/**
 * Checks of the licence for the agent governance.
 *
 * Without it, the rulings on agents do nothing: their configuration stays, visible and editable, and
 * applies again as soon as the licence allows it.
 */
@Component
class AgentsLicense(
    private val licenseControlService: LicenseControlService,
) {

    /**
     * Whether the licence allows the agent governance. Read on every call, never cached: a licence
     * which lapses stops the rulings at once.
     */
    val agentsEnabled: Boolean
        get() = licenseControlService.isFeatureEnabled(FEATURE_AGENTS)
}
