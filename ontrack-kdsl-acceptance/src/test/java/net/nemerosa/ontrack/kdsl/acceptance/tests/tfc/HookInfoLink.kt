package net.nemerosa.ontrack.kdsl.acceptance.tests.tfc

import tools.jackson.databind.JsonNode

/**
 * Contains information linked to a hook response. Used in hook records to follow-up on more information.
 *
 * @param feature ID of the extension feature which provides the information.
 * @param id ID of the HookInfoLinkExtension which provides the information.
 * @param data JSON data as understood by the HookInfoLinkExtension
 */
data class HookInfoLink(
        val feature: String,
        val id: String,
        val data: JsonNode,
)
