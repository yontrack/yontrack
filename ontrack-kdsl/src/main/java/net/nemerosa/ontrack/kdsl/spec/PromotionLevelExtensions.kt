package net.nemerosa.ontrack.kdsl.spec

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.PromotionLevelDeletePropertyMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.PromotionLevelGetPropertyQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.PromotionLevelSetPropertyMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.PromotionLevelFragment
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

/**
 * Creates a [PromotionLevel] from a GraphQL [PromotionLevelFragment].
 */
fun PromotionLevelFragment.toPromotionLevel(connected: Connected) = PromotionLevel(
    connector = connected.connector,
    branch = branch?.branchFragment?.toBranch(connected) ?: error("Missing parent branch"),
    id = id.toUInt(),
    name = name!!,
    description = description,
)

/**
 * Sets a generic property on a [PromotionLevel].
 *
 * @param type FQCN of the property type
 * @param data Property raw data (will be converted into JSON)
 */
fun PromotionLevel.setProperty(
    type: String,
    data: Any,
): PromotionLevel {
    graphqlConnector.mutate(
        PromotionLevelSetPropertyMutation(
            id.toInt(),
            type,
            data.asJson()
        )
    ) {
        it?.setPromotionLevelPropertyById?.payloadUserErrors?.convert()
    }
    return this
}

/**
 * Deletes a generic property on a [PromotionLevel].
 *
 * @param type FQCN of the property type
 */
fun PromotionLevel.deleteProperty(
    type: String,
): PromotionLevel {
    graphqlConnector.mutate(
        PromotionLevelDeletePropertyMutation(
            id.toInt(),
            type
        )
    ) {
        it?.setPromotionLevelPropertyById?.payloadUserErrors?.convert()
    }
    return this
}

/**
 * Gets a generic property on a [PromotionLevel].
 *
 * @param type FQCN of the property type
 * @return Property raw data as JSON
 */
fun PromotionLevel.getProperty(
    type: String,
): JsonNode? =
    graphqlConnector.query(
        PromotionLevelGetPropertyQuery(id.toInt(), type)
    )?.promotionLevel?.properties?.firstOrNull()?.value?.asJson()
