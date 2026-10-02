package net.nemerosa.ontrack.extension.audittrail.verification

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName

/**
 * A check of the verification of a trail which failed.
 *
 * @property seq Position of the entry, from 1 — its seq, unless the seqs themselves are wrong
 * @property type What failed
 * @property message What failed, for a human
 */
@APIName("AuditTrailVerificationProblem")
@APIDescription("A check of the verification of a trail which failed")
data class TrailVerificationProblem(
    @APIDescription("Position of the entry, from 1 - its seq, unless the seqs themselves are wrong")
    val seq: Int,
    @APIDescription("What failed")
    val type: TrailVerificationProblemType,
    @APIDescription("What failed, for a human")
    val message: String,
)
