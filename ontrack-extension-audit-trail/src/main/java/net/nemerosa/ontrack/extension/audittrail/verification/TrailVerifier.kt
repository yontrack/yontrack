package net.nemerosa.ontrack.extension.audittrail.verification

import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJsonException
import net.nemerosa.ontrack.extension.audittrail.endorsement.Endorsement
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstancePublicKey
import net.nemerosa.ontrack.extension.audittrail.endorsement.TrailEndorsementFormat
import net.nemerosa.ontrack.extension.audittrail.export.TrailExport
import net.nemerosa.ontrack.extension.audittrail.hash.TrailEnvelope
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import java.security.GeneralSecurityException
import java.security.PublicKey

/**
 * Verification of a trail from its entries, their endorsements and the public keys of the instance
 * alone — what is stored, or what an export holds. Nothing else is trusted.
 *
 * ## Algorithm
 *
 * For the entry at position *n* (from 1), in the order given:
 *
 * 1. **Seq** — its `seq` is *n*;
 * 2. **Schema version** — its `schemaVersion` is supported: `1` ([TrailHashFormatV1]);
 * 3. **Hash** — the hash of its envelope, computed with the format of its schema version, is its
 *    stored `hash`;
 * 4. **Previous hash** — its `prevHash` is `null` for *n* = 1, and the stored `hash` of the entry
 *    *n* − 1 otherwise;
 * 5. **First entry** — for *n* = 1, its `type` is `build.created` or `trail.opened`, and, when the
 *    build is known, its `payload.build.id` is the ID of the build;
 * 6. **Endorsements** — each of its endorsements names a known key, and is the Ed25519 signature
 *    of its stored `hash` by that key ([TrailEndorsementFormat]).
 *
 * Checks 1 to 5 are the chain: the trail is intact when none fails, and otherwise verified up to
 * the entry before the first which fails one. Check 6 is apart: a trail whose hashes were all
 * recomputed after an edit has an intact chain, and only its endorsements tell.
 *
 * An entry with no endorsement is not invalid — it was written while the instance key was not
 * provisioned. It is covered by the next endorsed entry, whose hash chains over it; the entries
 * after the last endorsed one are the unendorsed tail of the trail.
 */
object TrailVerifier {

    /**
     * An entry to verify.
     *
     * @property envelope What is hashed of the entry, as stored
     * @property hash Hash of the entry, as stored
     * @property endorsements Endorsements of the entry, as stored
     */
    data class Entry(
        val envelope: TrailEnvelope,
        val hash: String,
        val endorsements: List<Endorsement>,
    )

    /**
     * Verifies an exported trail, with its own keys, against the build it names.
     *
     * @param export Export of a trail
     * @return Result of the verification
     */
    fun verify(export: TrailExport): TrailVerification =
        verify(
            entries = export.entries.map { entry ->
                Entry(
                    envelope = entry.toEnvelope(),
                    hash = entry.hash,
                    endorsements = entry.endorsements.map { Endorsement(keyId = it.keyId, signature = it.signature) },
                )
            },
            keys = export.keys,
            buildId = export.build.id,
        )

    /**
     * Verifies a trail.
     *
     * @param entries Entries of the trail, in the order of their positions — never sorted here
     * @param keys Public keys of the instance
     * @param buildId ID of the build the trail must belong to, which its first entry names —
     * `null` not to check it
     * @return Result of the verification
     */
    fun verify(entries: List<Entry>, keys: List<InstancePublicKey>, buildId: Int?): TrailVerification {
        val publicKeys = parseKeys(keys)
        val problems = mutableListOf<TrailVerificationProblem>()
        entries.forEachIndexed { index, entry ->
            val position = index + 1
            fun problem(type: TrailVerificationProblemType, message: String) {
                problems += TrailVerificationProblem(seq = position, type = type, message = message)
            }

            val envelope = entry.envelope
            if (envelope.seq != position) {
                problem(TrailVerificationProblemType.SEQ, "Seq ${envelope.seq} found at position $position.")
            }
            when (val computed = hash(envelope)) {
                is HashResult.Unsupported -> problem(
                    TrailVerificationProblemType.SCHEMA_VERSION,
                    "Schema version ${envelope.schemaVersion} is not supported.",
                )

                is HashResult.Invalid -> problem(
                    TrailVerificationProblemType.HASH,
                    "The content of the entry cannot be hashed: ${computed.message}",
                )

                is HashResult.Hash -> if (computed.hash != entry.hash) {
                    problem(TrailVerificationProblemType.HASH, "The hash of the entry is not the hash of its content.")
                }
            }
            val expectedPrevHash = entries.getOrNull(index - 1)?.hash
            if (envelope.prevHash != expectedPrevHash) {
                problem(
                    TrailVerificationProblemType.PREVIOUS_HASH,
                    if (expectedPrevHash == null) {
                        "The first entry has a previous hash."
                    } else {
                        "The previous hash of the entry is not the hash of the entry before it."
                    },
                )
            }
            if (position == 1) {
                if (envelope.type != TrailEntryTypes.BUILD_CREATED && envelope.type != TrailEntryTypes.TRAIL_OPENED) {
                    problem(
                        TrailVerificationProblemType.FIRST_ENTRY,
                        "The first entry is ${envelope.type}, neither ${TrailEntryTypes.BUILD_CREATED} nor ${TrailEntryTypes.TRAIL_OPENED}.",
                    )
                }
                if (buildId != null) {
                    val namedBuild = envelope.payload.path("build").path("id")
                    if (!namedBuild.isIntegralNumber || namedBuild.asInt() != buildId) {
                        problem(TrailVerificationProblemType.BUILD, "The first entry does not name build $buildId.")
                    }
                }
            }
            entry.endorsements.forEach { endorsement ->
                val key = publicKeys[endorsement.keyId]
                if (key == null) {
                    problem(
                        TrailVerificationProblemType.UNKNOWN_KEY,
                        "Endorsed by key ${endorsement.keyId}, which is not a public key of the instance.",
                    )
                } else if (!endorses(key, entry.hash, endorsement.signature)) {
                    problem(
                        TrailVerificationProblemType.ENDORSEMENT,
                        "The endorsement by key ${endorsement.keyId} is not the signature of the hash of the entry.",
                    )
                }
            }
        }

        val firstBrokenSeq = problems.filter { it.type.chain }.minOfOrNull { it.seq }
        val firstInvalidEndorsementSeq = problems.filter { !it.type.chain }.minOfOrNull { it.seq }
        val lastEndorsed = entries.indexOfLast { it.endorsements.isNotEmpty() } + 1
        return TrailVerification(
            chainIntact = firstBrokenSeq == null,
            firstBrokenSeq = firstBrokenSeq,
            endorsementsValid = firstInvalidEndorsementSeq == null,
            firstInvalidEndorsementSeq = firstInvalidEndorsementSeq,
            partial = entries.firstOrNull()?.envelope?.type == TrailEntryTypes.TRAIL_OPENED,
            unendorsedFromSeq = (lastEndorsed + 1).takeIf { it <= entries.size },
            problems = problems.sortedBy { it.seq },
        )
    }

    private sealed interface HashResult {
        data class Hash(val hash: String) : HashResult
        data class Invalid(val message: String?) : HashResult
        data object Unsupported : HashResult
    }

    /**
     * Hash of an envelope, with the format of its schema version.
     */
    private fun hash(envelope: TrailEnvelope): HashResult =
        when (envelope.schemaVersion) {
            TrailHashFormatV1.SCHEMA_VERSION -> try {
                HashResult.Hash(TrailHashFormatV1.hash(envelope))
            } catch (e: CanonicalJsonException) {
                HashResult.Invalid(e.message)
            }

            else -> HashResult.Unsupported
        }

    private fun endorses(key: PublicKey, hash: String, signature: String): Boolean =
        try {
            TrailEndorsementFormat.verify(key, hash, signature)
        } catch (_: IllegalArgumentException) {
            // Not the hash of an entry
            false
        } catch (_: GeneralSecurityException) {
            false
        }

    /**
     * Public keys by ID. A key which cannot be read, or whose ID is not the ID of its key, is
     * ignored: what it endorsed is reported as endorsed by an unknown key.
     */
    private fun parseKeys(keys: List<InstancePublicKey>): Map<String, PublicKey> =
        keys.mapNotNull { key ->
            val publicKey = try {
                TrailEndorsementFormat.parsePublicKeyPem(key.publicKey)
            } catch (_: GeneralSecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            publicKey
                ?.takeIf { key.algorithm == TrailEndorsementFormat.ALGORITHM && TrailEndorsementFormat.keyId(it) == key.keyId }
                ?.let { key.keyId to it }
        }.toMap()
}
