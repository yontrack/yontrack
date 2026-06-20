package net.nemerosa.ontrack.kdsl.spec

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.GetPromotionRunFieldValuesQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

fun PromotionRun.getFieldValues(): List<PromotionRunFieldValue> =
    graphqlConnector.query(
        GetPromotionRunFieldValuesQuery(id.toInt())
    )?.promotionRuns?.firstOrNull()?.fieldValues?.map { fv ->
        PromotionRunFieldValue(
            name = fv.name,
            value = fv.value?.asJson(),
        )
    } ?: emptyList()
