package net.nemerosa.ontrack.extension.findings.graphql

import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumFindingSeverity : AbstractGQLEnum<FindingSeverity>(
    FindingSeverity::class,
    FindingSeverity.entries.toTypedArray(),
    "Severity of a security finding, as asserted by a scanner. UNKNOWN is what a scanner gives when it has no severity to give."
)
