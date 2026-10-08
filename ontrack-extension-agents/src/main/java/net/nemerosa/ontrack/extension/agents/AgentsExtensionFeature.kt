package net.nemerosa.ontrack.extension.agents

import net.nemerosa.ontrack.extension.scm.SCMExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtensionFeature
import net.nemerosa.ontrack.model.extension.ExtensionFeatureOptions
import org.springframework.stereotype.Component

/**
 * Agent governance: the licensed rulings on agents.
 *
 * The core of the agents - agent accounts, the actor on the record, the agent policy, the assisted
 * change of the builds - is not here and is not licensed. This extension holds what rules on them, under
 * the licensed feature [extension.agents][net.nemerosa.ontrack.extension.agents.license.AgentsLicensedFeatureProvider.FEATURE_AGENTS].
 */
@Component
class AgentsExtensionFeature(
    scmExtensionFeature: SCMExtensionFeature,
) : AbstractExtensionFeature(
    id = "agents",
    name = "Agent governance",
    description = "Licensed rulings on agents: conditioning a promotion level on the assisted change of the builds",
    options = ExtensionFeatureOptions.DEFAULT
        .withDependency(scmExtensionFeature),
)
