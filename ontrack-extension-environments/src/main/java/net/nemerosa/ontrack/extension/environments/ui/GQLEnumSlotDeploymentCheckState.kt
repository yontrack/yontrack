package net.nemerosa.ontrack.extension.environments.ui

import net.nemerosa.ontrack.extension.environments.SlotDeploymentCheckState
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumSlotDeploymentCheckState : AbstractGQLEnum<SlotDeploymentCheckState>(
    type = SlotDeploymentCheckState::class,
    values = SlotDeploymentCheckState.entries.toTypedArray(),
    description = "State of a deployment check: OK, still pending (it blocks, but is expected to pass), or failed",
)
