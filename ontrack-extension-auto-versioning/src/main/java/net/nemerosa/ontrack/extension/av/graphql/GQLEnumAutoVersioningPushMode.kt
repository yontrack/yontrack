package net.nemerosa.ontrack.extension.av.graphql

import net.nemerosa.ontrack.extension.av.config.AutoVersioningPushMode
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumAutoVersioningPushMode : AbstractGQLEnum<AutoVersioningPushMode>(
    AutoVersioningPushMode::class,
    AutoVersioningPushMode.entries.toTypedArray(),
    "Way the auto-versioning update is pushed to the target branch."
)
