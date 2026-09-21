package net.nemerosa.ontrack.extension.hook

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription

/**
 * Contains information linked to a hook response. Used in hook records to follow-up on more information.
 *
 * @param feature ID of the [ExtensionFeature] which provides the information.
 * @param id ID of the [HookInfoLinkExtension] which provides the information.
 * @param data JSON data as understood by the [HookInfoLinkExtension]
 */
data class HookInfoLink(
        @APIDescription("ID of the ExtensionFeature which provides the information.")
        val feature: String,
        @APIDescription("ID of the HookInfoLinkExtension which provides the information.")
        val id: String,
        @APIDescription("JSON data as understood by the HookInfoLinkExtension")
        val data: JsonNode,
)
