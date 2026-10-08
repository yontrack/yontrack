package net.nemerosa.ontrack.extension.agents.license

import net.nemerosa.ontrack.extension.license.LicensedFeatureProvider
import net.nemerosa.ontrack.extension.license.ProvidedLicensedFeature
import org.springframework.stereotype.Component

/**
 * The licensed feature of the agent governance: one boolean feature, with no licence data - no quota,
 * no count.
 *
 * It gates every ruling on agents - the promotion condition on assisted builds, the stamp restriction
 * on agent-recorded evidence - and the agent activity views. Recording agents is not gated.
 *
 * Every licensed feature is enabled by the development licence, and therefore in the dev profile
 * and in every test stack.
 */
@Component
class AgentsLicensedFeatureProvider : LicensedFeatureProvider {

    override val providedFeatures: List<ProvidedLicensedFeature> = listOf(
        ProvidedLicensedFeature(
            id = FEATURE_AGENTS,
            name = FEATURE_AGENTS_NAME,
        )
    )

    companion object {
        /**
         * ID of the licensed feature of the agent governance
         */
        const val FEATURE_AGENTS = "extension.agents"

        /**
         * Display name of the licensed feature of the agent governance
         */
        const val FEATURE_AGENTS_NAME = "Agent governance"
    }
}
