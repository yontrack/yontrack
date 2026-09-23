package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumFindingState : AbstractGQLEnum<FindingState>(
    FindingState::class,
    FindingState.entries.toTypedArray(),
    "State of a security finding in its project, rolled up from its exposure on the branches which count, or on one branch. OPEN: exposed without any acceptance holding. ACCEPTED: not open, but exposed under an acceptance which holds. RESOLVED: neither open nor accepted."
)
