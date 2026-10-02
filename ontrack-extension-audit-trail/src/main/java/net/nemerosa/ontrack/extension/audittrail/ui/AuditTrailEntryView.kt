package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntry
import tools.jackson.databind.JsonNode

/**
 * An entry of a trail, in GraphQL: as exported, its time as hashed.
 */
@APIName("AuditTrailEntry")
@APIDescription("An entry of the trail of a build")
data class AuditTrailEntryView(
    @APIDescription("Technical ID of the entry")
    val id: Int,
    @APIDescription("Position of the entry in the trail, from 1")
    val seq: Int,
    @APIDescription("Schema version of the entry, which selects its hash format")
    val schemaVersion: Int,
    @APIDescription("Type of the entry, like build.created or validation.run")
    val type: String,
    @APIDescription("Server time of the entry, as hashed: ISO-8601 in UTC with milliseconds")
    val time: String,
    @APIDescription("Who made the change")
    val actor: JsonNode,
    @APIDescription("What changed")
    val payload: JsonNode,
    @APIDescription("Hash of the entry before, null for seq 1")
    val prevHash: String?,
    @APIDescription("Hash of the entry: SHA-256 of the canonical form of its envelope, in lowercase hex")
    val hash: String,
) {
    companion object {
        fun of(entry: TrailEntry): AuditTrailEntryView {
            val envelope = entry.envelope
            return AuditTrailEntryView(
                id = entry.id,
                seq = envelope.seq,
                schemaVersion = envelope.schemaVersion,
                type = envelope.type,
                time = envelope.time,
                actor = envelope.actor,
                payload = envelope.payload,
                prevHash = envelope.prevHash,
                hash = entry.hash,
            )
        }
    }
}
