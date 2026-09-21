package net.nemerosa.ontrack.kdsl.spec

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ValidationStampDeletePropertyMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ValidationStampGetPropertyQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ValidationStampSetPropertyMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.ValidationStampFragment
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector


/**
 * Creates a [ValidationStamp] from a GraphQL [ValidationStampFragment].
 */
fun ValidationStampFragment.toValidationStamp(connected: Connected) = ValidationStamp(
    connector = connected.connector,
    id = id.toUInt(),
    name = name!!,
    description = description,
    dataType = dataType?.descriptor?.id,
    dataTypeConfig = dataType?.config?.asJson(),
)


/**
 * Sets a generic property on a [ValidationStamp].
 *
 * @param type FQCN of the property type
 * @param data Property raw data (will be converted into JSON)
 */
fun ValidationStamp.setProperty(
    type: String,
    data: Any,
): ValidationStamp {
    graphqlConnector.mutate(
        ValidationStampSetPropertyMutation(
            id.toInt(),
            type,
            data.asJson()
        )
    ) {
        it?.setValidationStampPropertyById?.payloadUserErrors?.convert()
    }
    return this
}

/**
 * Deletes a generic property on a [ValidationStamp].
 *
 * @param type FQCN of the property type
 */
fun ValidationStamp.deleteProperty(
    type: String,
): ValidationStamp {
    graphqlConnector.mutate(
        ValidationStampDeletePropertyMutation(
            id.toInt(),
            type
        )
    ) {
        it?.setValidationStampPropertyById?.payloadUserErrors?.convert()
    }
    return this
}

/**
 * Gets a generic property on a [ValidationStamp].
 *
 * @param type FQCN of the property type
 * @return Property raw data as JSON
 */
fun ValidationStamp.getProperty(
    type: String,
): JsonNode? =
    graphqlConnector.query(
        ValidationStampGetPropertyQuery(id.toInt(), type)
    )?.validationStamp?.properties?.firstOrNull()?.value?.asJson()
