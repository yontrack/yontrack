package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.findings.model.FindingResolutionReason
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumFindingResolutionReason : AbstractGQLEnum<FindingResolutionReason>(
    FindingResolutionReason::class,
    FindingResolutionReason.entries.toTypedArray(),
    "Why a security finding was resolved on a branch. ABSENT: the latest scan of the stamp on the branch does not report it."
)
