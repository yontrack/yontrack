package net.nemerosa.ontrack.kdsl.spec

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLMissingDataException
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.BuildReadinessQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector

/**
 * What a build still lacks to reach a promotion level or a slot.
 *
 * @property ready Whether nothing is missing
 * @property missing What is missing, empty when [ready] is true
 */
data class Readiness(
    val ready: Boolean,
    val missing: List<ReadinessItem>,
)

/**
 * One thing a build still lacks.
 *
 * @property kind Kind of the missing condition
 * @property name Name of what is missing: stamp, promotion level, check, admission rule...
 * @property message Explanation of what is missing
 */
data class ReadinessItem(
    val kind: ReadinessKind,
    val name: String,
    val message: String,
)

/**
 * Kind of a missing condition.
 */
enum class ReadinessKind {
    VALIDATION,
    PROMOTION,
    CHECK,
    ADMISSION_RULE,
    MANUAL,
    AGENT_POLICY,
}

/**
 * Readiness of a build for a promotion level or for a slot - exactly one of them.
 */
internal fun Build.queryReadiness(promotionLevel: String?, slotId: String?): Readiness =
    graphqlConnector.query(
        BuildReadinessQuery(
            buildId = id.toInt(),
            promotionLevel = Optional.presentIfNotNull(promotionLevel),
            slotId = Optional.presentIfNotNull(slotId),
        )
    )?.build?.readiness?.let { readiness ->
        Readiness(
            ready = readiness.ready,
            missing = readiness.missing.map { item ->
                ReadinessItem(
                    kind = ReadinessKind.valueOf(item.kind.rawValue),
                    name = item.name,
                    message = item.message,
                )
            },
        )
    } ?: throw GraphQLMissingDataException("Did not get back the readiness of the build")
