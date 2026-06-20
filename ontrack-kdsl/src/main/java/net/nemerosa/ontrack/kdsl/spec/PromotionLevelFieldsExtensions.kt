package net.nemerosa.ontrack.kdsl.spec

import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.GetPromotionLevelFieldsQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.SetPromotionLevelFieldsMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.PromotionLevelFieldInput
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

fun PromotionLevel.setFields(fields: List<PromotionLevelFieldInput>) {
    graphqlConnector.mutate(
        SetPromotionLevelFieldsMutation(
            id.toInt(),
            fields,
        )
    ) { it?.setPromotionLevelFields?.payloadUserErrors?.convert() }
}

fun PromotionLevel.getFields(): List<PromotionLevelField> =
    graphqlConnector.query(
        GetPromotionLevelFieldsQuery(id.toInt())
    )?.promotionLevel?.fields?.map { f ->
        PromotionLevelField(
            name = f.name,
            displayName = f.displayName,
            description = f.description,
            type = PromotionLevelFieldType.valueOf(f.type.name),
            required = f.required,
            options = f.options ?: emptyList(),
            position = f.position,
        )
    } ?: emptyList()
