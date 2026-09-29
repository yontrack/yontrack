package net.nemerosa.ontrack.kdsl.spec.admin

import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.PredefinedPromotionLevelByNameQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import java.net.URL
import java.util.*

/**
 * Management of the predefined promotion levels
 */
class PredefinedPromotionLevelsMgt(connector: Connector) : Connected(connector) {

    /**
     * Creates a predefined promotion level if it does not exist yet.
     *
     * @param name Name of the promotion level
     * @param description Description of the promotion level
     * @param image URL to the image to set
     */
    fun createPredefinedPromotionLevel(
        name: String,
        description: String = "",
        image: URL? = null,
        override: Boolean = false,
    ) {
        // Gets an existing predefined promotion level by name
        val existing = graphqlConnector.query(
            PredefinedPromotionLevelByNameQuery(name)
        )?.predefinedPromotionLevelByName
        // Overriding?
        var create = existing == null
        if (existing != null && override) {
            create = true
            connector.delete("/rest/admin/predefinedPromotionLevels/${existing.id}")
        }
        // Creating
        if (create) {
            val pplId = connector.post(
                "/rest/admin/predefinedPromotionLevels/create",
                body = mapOf(
                    "name" to name,
                    "description" to description,
                )
            ).body.asJson().path("id").asInt()
            // Image
            if (image != null) {
                setPredefinedPromotionLevelImage(pplId, image.readBytes())
            }
        }
    }

    /**
     * Sets the image of a predefined promotion level.
     *
     * @param id ID of the predefined promotion level
     * @param image PNG content of the image
     */
    fun setPredefinedPromotionLevelImage(id: Int, image: ByteArray) {
        connector.put(
            "/rest/admin/predefinedPromotionLevels/${id}/image",
            body = Base64.getEncoder().encodeToString(image),
        )
    }

}