package net.nemerosa.ontrack.extension.av.graphql

import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Deprecated("Removed in V7. Use GQLEnumAutoVersioningRefRole instead. See #2130")
@Component
class GQLEnumAutoVersioningDirection : AbstractGQLEnum<AutoVersioningDirection>(
    AutoVersioningDirection::class,
    AutoVersioningDirection.values(),
    "What the reference build or branch is when getting auto versioning information between two builds: DOWN for a consumer, UP for a dependency."
)
