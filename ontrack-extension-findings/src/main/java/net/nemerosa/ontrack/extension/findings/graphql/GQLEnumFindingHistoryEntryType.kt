package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.findings.history.FindingHistoryEntryType
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumFindingHistoryEntryType : AbstractGQLEnum<FindingHistoryEntryType>(
    FindingHistoryEntryType::class,
    FindingHistoryEntryType.entries.toTypedArray(),
    "Type of an entry in the history of a security finding. " +
            "DISCOVERED: start of the first period of the first exposure of the finding. " +
            "EXPOSED: start of the first period of an exposure on another branch or stamp. " +
            "REOPENED: start of a later period of an exposure. " +
            "RESOLVED: end of a period. " +
            "ACCEPTED: first observation of a period under an acceptance. " +
            "ACCEPTANCE_WITHDRAWN: observation without acceptance after accepted ones. " +
            "ACCEPTANCE_EXPIRED: day after the last day of an acceptance, evaluated when read. " +
            "OBSERVATIONS: consecutive observations of a period between two other entries."
)
