package net.nemerosa.ontrack.extension.scorecard.graphql

import net.nemerosa.ontrack.extension.scorecard.engine.MarkerKind
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumMarkerKind : AbstractGQLEnum<MarkerKind>(
    MarkerKind::class,
    MarkerKind.entries.toTypedArray(),
    "Kind of the event a delivery reading measures up to: PROMOTION (a promotion granted) or ENVIRONMENT (a deployment done in an environment)."
)
