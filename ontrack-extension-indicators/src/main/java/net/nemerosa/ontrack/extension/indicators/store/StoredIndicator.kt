package net.nemerosa.ontrack.extension.indicators.store

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.Signature

/**
 * Representation of a stored indicator.
 *
 * @property value Value being stored. Can be `null` if not applicable.
 * @property comment Optional comment
 * @property signature Date/user for the entry
 */
class StoredIndicator(
        val value: JsonNode?,
        val comment: String?,
        val signature: Signature
)