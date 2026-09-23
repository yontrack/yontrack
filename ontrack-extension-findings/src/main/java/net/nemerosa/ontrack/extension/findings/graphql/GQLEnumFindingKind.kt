package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.findings.model.FindingKind
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumFindingKind : AbstractGQLEnum<FindingKind>(
    FindingKind::class,
    FindingKind.entries.toTypedArray(),
    "Kind of scan which reported a security finding."
)
