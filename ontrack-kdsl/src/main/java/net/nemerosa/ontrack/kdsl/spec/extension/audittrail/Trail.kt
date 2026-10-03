package net.nemerosa.ontrack.kdsl.spec.extension.audittrail

import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.TrailVerificationProblemType
import tools.jackson.databind.JsonNode

/**
 * The trail of a build: the append-only, hash-chained record of its story.
 *
 * @property entries Entries of the trail, by seq
 * @property endorsements Endorsements of the entries, by seq — none for the entries written while
 * the instance key was not provisioned
 */
data class Trail(
    val entries: List<TrailEntry>,
    val endorsements: List<TrailEndorsement>,
)

/**
 * One entry of a trail.
 *
 * @property id Technical ID of the entry
 * @property seq Position of the entry in the trail, from 1
 * @property schemaVersion Schema version of the entry, which selects its hash format
 * @property type Type of the entry, like `build.created` or `evidence.attached`
 * @property time Server time of the entry, as hashed: ISO-8601 in UTC with milliseconds
 * @property actor Who made the change
 * @property prevHash Hash of the entry before, `null` for seq 1
 * @property payload What changed
 * @property hash Hash of the entry: SHA-256 of the canonical form of its envelope, in lowercase hex
 */
data class TrailEntry(
    val id: Int,
    val seq: Int,
    val schemaVersion: Int,
    val type: String,
    val time: String,
    val actor: JsonNode,
    val prevHash: String?,
    val payload: JsonNode,
    val hash: String,
)

/**
 * Endorsement of an entry by the instance key: the Ed25519 signature of its hash.
 *
 * @property entryId Technical ID of the endorsed entry
 * @property seq Seq of the endorsed entry
 * @property keyId ID of the key which endorsed the entry
 * @property signature Signature of the hash of the entry, in base64
 * @property time Server time of the endorsement: ISO-8601 in UTC with milliseconds
 */
data class TrailEndorsement(
    val entryId: Int,
    val seq: Int,
    val keyId: String,
    val signature: String,
    val time: String,
)

/**
 * Verification of the trail of a build.
 *
 * @property chainIntact Whether every hash recomputes from its entry, every entry is chained to the
 * one before it, and the seqs run from 1 without gap
 * @property firstBrokenSeq Position of the first entry failing a check of the chain, `null` when the
 * chain is intact
 * @property endorsementsValid Whether every endorsement is the signature of the hash of its entry by
 * a known public key of the instance
 * @property firstInvalidEndorsementSeq Position of the first entry with an invalid endorsement,
 * `null` when they are all valid
 * @property partial Whether the trail opens with `trail.opened`: its build predates it
 * @property unendorsedFromSeq Position of the first entry of the unendorsed tail of the trail,
 * `null` when the last entry is endorsed or the trail is empty
 * @property missingEvidence Positions of the `evidence.attached` entries whose evidence is absent
 * from the storage — `null` unless the evidence was verified
 * @property alteredEvidence Positions of the `evidence.attached` entries whose evidence no longer
 * has the recorded digest — `null` unless the evidence was verified
 * @property problems Every check which failed, by position
 */
data class TrailVerification(
    val chainIntact: Boolean,
    val firstBrokenSeq: Int?,
    val endorsementsValid: Boolean,
    val firstInvalidEndorsementSeq: Int?,
    val partial: Boolean,
    val unendorsedFromSeq: Int?,
    val missingEvidence: List<Int>?,
    val alteredEvidence: List<Int>?,
    val problems: List<TrailVerificationProblem>,
)

/**
 * A check of the verification of a trail which failed.
 *
 * @property seq Position of the entry, from 1
 * @property type What failed
 * @property message What failed, for a human
 */
data class TrailVerificationProblem(
    val seq: Int,
    val type: TrailVerificationProblemType,
    val message: String,
)
