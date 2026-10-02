package net.nemerosa.ontrack.extension.audittrail.export

import net.nemerosa.ontrack.extension.audittrail.endorsement.InstancePublicKey
import net.nemerosa.ontrack.extension.audittrail.hash.TrailEnvelope
import tools.jackson.databind.JsonNode

/**
 * The JSON export of the trail of a build: self-sufficient, it is verified offline — by
 * `yontrack audit-trail verify` — with nothing but its own content
 * ([TrailVerifier.verify][net.nemerosa.ontrack.extension.audittrail.verification.TrailVerifier.verify]).
 *
 * **Its shape is a compatibility contract**, like the hash format: a change to it is a new
 * [exportVersion].
 *
 * ```
 * {
 *   "exportVersion": 1,
 *   "exportedAt": "2026-10-02T09:00:00.000Z",
 *   "build": {"id": 1042, "project": "payments", "branch": "release-2.4", "name": "2.4.7"},
 *   "keys": [{"keyId": "...", "algorithm": "Ed25519", "publicKey": "-----BEGIN PUBLIC KEY-----\n..."}],
 *   "entries": [
 *     {
 *       "seq": 1, "schemaVersion": 1, "type": "build.created", "time": "2026-10-02T08:15:30.000Z",
 *       "actor": {...}, "prevHash": null, "payload": {...},
 *       "hash": "4b320b3f...",
 *       "endorsements": [{"keyId": "...", "signature": "...", "time": "2026-10-02T08:15:30.000Z"}]
 *     }
 *   ]
 * }
 * ```
 *
 * The fields of an entry but `hash` and `endorsements` are its envelope, as hashed: the hash
 * format of its `schemaVersion` recomputes its `hash` from them alone.
 *
 * The shared test vectors of `audit-trail/test-vectors/exports/` are exports: `01-build-created.json`
 * is intact and validly endorsed by the key of RFC 8032; `02-tampered-payload.json` is the same
 * export whose seq 2 payload was edited — its chain is broken at seq 2.
 *
 * @property exportVersion Version of the shape of this document: [EXPORT_VERSION]
 * @property exportedAt Server time of the export, as the times of the entries
 * @property build Build of the trail, which the first entry names
 * @property keys Public keys of the instance, with which the endorsements are verified
 * @property entries Entries of the trail, by seq
 */
data class TrailExport(
    val exportVersion: Int,
    val exportedAt: String,
    val build: TrailExportBuild,
    val keys: List<InstancePublicKey>,
    val entries: List<TrailExportEntry>,
) {
    companion object {
        /**
         * Version of the shape of the exports written by this Yontrack
         */
        const val EXPORT_VERSION = 1
    }
}

/**
 * Build of an exported trail.
 *
 * @property id ID of the build
 * @property project Name of its project
 * @property branch Name of its branch
 * @property name Name of the build
 */
data class TrailExportBuild(
    val id: Int,
    val project: String,
    val branch: String,
    val name: String,
)

/**
 * An exported entry: its envelope, its hash and its endorsements.
 *
 * @property seq Position of the entry in the trail, from 1
 * @property schemaVersion Schema version of the entry, which selects its hash format
 * @property type Type of the entry
 * @property time Server time of the entry, as hashed
 * @property actor Who made the change
 * @property prevHash Hash of the entry before, `null` for seq 1
 * @property payload What changed
 * @property hash Hash of the entry
 * @property endorsements Endorsements of the entry — none when it was written while the instance
 * key was not provisioned
 */
data class TrailExportEntry(
    val seq: Int,
    val schemaVersion: Int,
    val type: String,
    val time: String,
    val actor: JsonNode,
    val prevHash: String?,
    val payload: JsonNode,
    val hash: String,
    val endorsements: List<TrailExportEndorsement>,
) {
    /**
     * What is hashed of this entry.
     */
    fun toEnvelope() = TrailEnvelope(
        schemaVersion = schemaVersion,
        seq = seq,
        type = type,
        time = time,
        actor = actor,
        prevHash = prevHash,
        payload = payload,
    )
}

/**
 * An exported endorsement.
 *
 * @property keyId ID of the key which endorsed the entry
 * @property signature Signature of the hash of the entry, in base64
 * @property time Server time of the endorsement, as the times of the entries
 */
data class TrailExportEndorsement(
    val keyId: String,
    val signature: String,
    val time: String,
)
