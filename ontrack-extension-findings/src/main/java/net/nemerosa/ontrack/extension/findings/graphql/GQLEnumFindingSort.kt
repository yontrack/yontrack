package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.findings.query.FindingSort
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumFindingSort : AbstractGQLEnum<FindingSort>(
    FindingSort::class,
    FindingSort.entries.toTypedArray(),
    "Order of the findings of a project. DEFAULT: the most severe first, then the most recently seen. EXPOSED_FOR: the longest exposed first, by their longest ongoing period on one branch for one stamp, then in the default order; the findings without any ongoing period, the resolved ones among them, come last."
)
