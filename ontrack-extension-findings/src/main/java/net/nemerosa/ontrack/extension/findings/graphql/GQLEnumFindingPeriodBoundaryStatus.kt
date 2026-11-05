package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.findings.model.FindingPeriodBoundaryStatus
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumFindingPeriodBoundaryStatus : AbstractGQLEnum<FindingPeriodBoundaryStatus>(
    FindingPeriodBoundaryStatus::class,
    FindingPeriodBoundaryStatus.entries.toTypedArray(),
    "How a period of an exposure which ended reads against a build filter. MATCHED: a build matches, at or after the end of the period and before the next one. NONE_YET: no build matches yet, and the finding has not come back. NOT_REACHED: the finding came back before any build matched. NOT_APPLICABLE: the filter cannot apply to this branch."
)
