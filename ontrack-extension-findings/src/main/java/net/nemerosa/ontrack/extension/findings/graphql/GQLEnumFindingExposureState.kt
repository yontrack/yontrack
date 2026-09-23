package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.findings.model.FindingExposureState
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumFindingExposureState : AbstractGQLEnum<FindingExposureState>(
    FindingExposureState::class,
    FindingExposureState.entries.toTypedArray(),
    "State of the exposure of a security finding on a branch, for the scans of one stamp. EXPOSED: reported by the latest scan without any acceptance holding. ACCEPTED: reported by the latest scan under an acceptance which holds. RESOLVED: no longer reported by the latest scan."
)
