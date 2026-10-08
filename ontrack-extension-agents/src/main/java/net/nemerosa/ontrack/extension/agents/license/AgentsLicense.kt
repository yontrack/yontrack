package net.nemerosa.ontrack.extension.agents.license

import net.nemerosa.ontrack.extension.agents.license.AgentsLicensedFeatureProvider.Companion.FEATURE_AGENTS
import net.nemerosa.ontrack.extension.license.control.LicenseControlService
import net.nemerosa.ontrack.extension.license.control.LicenseFeatureException
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

    /**
     * Checks that the licence allows the agent governance - for the reads which are refused without it,
     * like the activity of the agents, rather than doing nothing.
     *
     * @throws LicenseFeatureException When it does not
     */
    fun checkAgents() {
        if (!agentsEnabled) {
            throw LicenseFeatureException(FEATURE_AGENTS)
        }
    }
}
