package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.model.structure.Build
import tools.jackson.databind.JsonNode

/**
 * An entry to append to the trail of a build, as mapped from an event — its actor and time are
 * taken when it is appended.
 *
 * @property build Build whose trail is appended to
 * @property type Type of the entry
 * @property payload Payload of the entry
 */
data class TrailEntryRequest(
    val build: Build,
    val type: String,
    val payload: JsonNode,
)
