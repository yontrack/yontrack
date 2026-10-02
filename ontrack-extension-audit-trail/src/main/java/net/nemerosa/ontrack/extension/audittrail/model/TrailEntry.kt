package net.nemerosa.ontrack.extension.audittrail.model

import net.nemerosa.ontrack.extension.audittrail.hash.TrailEnvelope
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * One element of the trail of a build.
 *
 * @property id Technical ID of the entry
 * @property buildId ID of the build whose trail this entry belongs to
 * @property seq Position of the entry in the trail, from 1, without gap
 * @property schemaVersion Schema version of the entry, which selects its hash format
 * @property type Type of the entry (see [TrailEntryTypes])
 * @property payload What changed, canonical JSON object
 * @property actor Who made the change, canonical JSON object
 * @property time Server time of the entry, UTC, at the millisecond
 * @property prevHash Hash of the entry before, `null` for seq 1
 * @property hash Hash of the entry
 */
data class TrailEntry(
    val id: Int,
    val buildId: Int,
    val seq: Int,
    val schemaVersion: Int,
    val type: String,
    val payload: JsonNode,
    val actor: JsonNode,
    val time: LocalDateTime,
    val prevHash: String?,
    val hash: String,
) {
    /**
     * What is hashed of this entry.
     */
    val envelope: TrailEnvelope
        get() = TrailEnvelope(
            schemaVersion = schemaVersion,
            seq = seq,
            type = type,
            time = TrailHashFormatV1.formatTime(time),
            actor = actor,
            prevHash = prevHash,
            payload = payload,
        )
}
