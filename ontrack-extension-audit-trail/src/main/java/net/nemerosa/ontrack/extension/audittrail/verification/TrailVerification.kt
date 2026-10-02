package net.nemerosa.ontrack.extension.audittrail.verification

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName

/**
 * Result of the verification of a trail (see [TrailVerifier]).
 *
 * The chain and the endorsements are checked apart: a trail whose rows were edited without
 * recomputing the hashes has a broken chain, and one whose hashes were recomputed after the edit
 * has an intact chain but invalid endorsements.
 *
 * @property chainIntact Whether every hash recomputes from its entry, every entry is chained to
 * the one before it, and the seqs run from 1 without gap
 * @property firstBrokenSeq Position, from 1, of the first entry failing a check of the chain —
 * the trail is verified up to the one before it. `null` when the chain is intact.
 * @property endorsementsValid Whether every endorsement is the signature of the hash of its entry by
 * a known public key of the instance
 * @property firstInvalidEndorsementSeq Position of the first entry with an invalid endorsement,
 * `null` when they are all valid
 * @property partial Whether the trail opens with `trail.opened`: its build predates it, and the trail
 * starts at seq 1 on the time of that entry
 * @property unendorsedFromSeq Position of the first entry of the unendorsed tail of the trail — the
 * entries after the last endorsed one, which no endorsement covers. An unendorsed entry followed by
 * an endorsed one is covered through the chain. `null` when the last entry is endorsed, or when
 * the trail is empty.
 * @property problems Every check which failed, by position
 * @property missingEvidence Positions of the `evidence.attached` entries whose evidence is absent
 * from the storage — `null` unless the evidence was verified
 * @property alteredEvidence Positions of the `evidence.attached` entries whose evidence no longer
 * has the recorded digest — `null` unless the evidence was verified
 */
@APIName("AuditTrailVerification")
@APIDescription("Verification of the trail of a build")
data class TrailVerification(
    @APIDescription("Whether every hash recomputes from its entry, every entry is chained to the one before it, and the seqs run from 1 without gap")
    val chainIntact: Boolean,
    @APIDescription("Position, from 1, of the first entry failing a check of the chain - the trail is verified up to the one before it. Null when the chain is intact.")
    val firstBrokenSeq: Int?,
    @APIDescription("Whether every endorsement is the signature of the hash of its entry by a known public key of the instance")
    val endorsementsValid: Boolean,
    @APIDescription("Position of the first entry with an invalid endorsement, null when they are all valid")
    val firstInvalidEndorsementSeq: Int?,
    @APIDescription("Whether the trail opens with trail.opened: its build predates it, and the trail starts at seq 1")
    val partial: Boolean,
    @APIDescription("Position of the first entry of the unendorsed tail of the trail - the entries after the last endorsed one, which no endorsement covers. Null when the last entry is endorsed or the trail is empty.")
    val unendorsedFromSeq: Int?,
    @APIDescription("Every check which failed, by position")
    val problems: List<TrailVerificationProblem>,
    @APIDescription("Positions of the evidence.attached entries whose evidence is absent from the storage - null unless the evidence was verified")
    val missingEvidence: List<Int>? = null,
    @APIDescription("Positions of the evidence.attached entries whose evidence no longer has the recorded digest - null unless the evidence was verified")
    val alteredEvidence: List<Int>? = null,
)
