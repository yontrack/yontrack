package net.nemerosa.ontrack.extension.av.graphql

import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumAutoVersioningRefRole : AbstractGQLEnum<AutoVersioningRefRole>(
    AutoVersioningRefRole::class,
    AutoVersioningRefRole.entries.toTypedArray(),
    "What the reference build or branch is when getting auto versioning information between two builds."
)
