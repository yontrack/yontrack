package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerificationProblemType
import net.nemerosa.ontrack.graphql.schema.AbstractGQLEnum
import org.springframework.stereotype.Component

@Component
class GQLEnumTrailVerificationProblemType : AbstractGQLEnum<TrailVerificationProblemType>(
    TrailVerificationProblemType::class,
    TrailVerificationProblemType.entries.toTypedArray(),
    "Check of the verification of a trail which failed: SEQ, SCHEMA_VERSION, HASH, PREVIOUS_HASH, FIRST_ENTRY and BUILD break its chain; ENDORSEMENT and UNKNOWN_KEY invalidate an endorsement."
)
