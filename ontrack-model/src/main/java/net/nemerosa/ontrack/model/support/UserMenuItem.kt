package net.nemerosa.ontrack.model.support

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.extension.ExtensionFeature

/**
 * User menu item
 */
data class UserMenuItem(
    val groupId: String,
    val extension: String,
    val id: String,
    val name: String,
    val local: Boolean = false,
    val arguments: JsonNode? = null,
) {
    constructor(
        groupId: String,
        extension: ExtensionFeature,
        id: String,
        name: String,
        local: Boolean = false,
        arguments: JsonNode? = null,
    ) : this(
        groupId = groupId,
        extension = "extension/${extension.id}",
        id = id,
        name = name,
        local = local,
        arguments = arguments,
    )
}