package net.nemerosa.ontrack.extension.audittrail.hash

import tools.jackson.databind.JsonNode

/**
 * What is hashed of an entry: everything but its hash. See [TrailHashFormatV1].
 *
 * @property schemaVersion Schema version of the entry, which selects its hash format
 * @property seq Position of the entry in the trail of its build, from 1
 * @property type Type of the entry
 * @property time Server time of the entry, as hashed ([TrailHashFormatV1.formatTime])
 * @property actor Who made the change
 * @property prevHash Hash of the entry before, `null` for seq 1
 * @property payload What changed
 */
data class TrailEnvelope(
    val schemaVersion: Int,
    val seq: Int,
    val type: String,
    val time: String,
    val actor: JsonNode,
    val prevHash: String?,
    val payload: JsonNode,
)
