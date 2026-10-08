package net.nemerosa.ontrack.extension.scm.graphql

import net.nemerosa.ontrack.extension.scm.changelog.assistants.AssistedChangeBasis
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumAssistedChangeBasis : AbstractGQLEnum<AssistedChangeBasis>(
    type = AssistedChangeBasis::class,
    values = AssistedChangeBasis.entries.toTypedArray(),
    description = "How the assisted change of a build was obtained: COMPUTED by Yontrack from the change log, SET_BY_CI, or UNKNOWN when it could not be computed (see its reason)."
)
