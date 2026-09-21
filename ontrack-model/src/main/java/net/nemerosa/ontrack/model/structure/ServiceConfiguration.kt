package net.nemerosa.ontrack.model.structure

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.model.docs.SelfDocumented

/**
 * Association between a configuration service ID and an actual configuration data.
 */
@SelfDocumented
data class ServiceConfiguration(
    @APIDescription("ID of the service")
    val id: String,
    @APIDescription("Configuration of the service")
    val data: JsonNode?
) {
    companion object {
        @JvmStatic
        fun of(node: JsonNode): ServiceConfiguration {
            return ServiceConfiguration(
                node.get("id").asText(),
                node.get("data")
            )
        }
    }
}
