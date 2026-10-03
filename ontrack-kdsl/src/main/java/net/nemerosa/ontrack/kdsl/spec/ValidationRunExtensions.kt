package net.nemerosa.ontrack.kdsl.spec

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ChangeValidationRunStatusMutation
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.fragment.ValidationRunFragment
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Creates a [ValidationRun] from a GraphQL [ValidationRunFragment].
 */
fun ValidationRunFragment.toValidationRun(connected: Connected) = ValidationRun(
    connector = connected.connector,
    id = id.toUInt(),
    description = description,
    data = data?.let { data ->
        ValidationRunData(
            type = data.descriptor!!.id!!,
            data = data.data.asJson(),
        )
    },
    time = creation?.time?.let(::parseSignatureTime),
    statuses = validationRunStatuses.map {
        ValidationRunStatus(
            id = it.statusID!!.id,
            description = it.description ?: "",
            annotatedDescription = it.annotatedDescription ?: "",
        )
    }
)

/**
 * A signature's time is an ISO string, with or without a zone - UTC when without.
 */
private fun parseSignatureTime(time: String): LocalDateTime =
    try {
        LocalDateTime.parse(time, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } catch (_: DateTimeParseException) {
        LocalDateTime.ofInstant(Instant.parse(time), ZoneOffset.UTC)
    }

/**
 * Changes the status of this validation run — a FAILED run passed after a look at it, for example.
 * The run keeps its previous statuses: the new one is added on top of them.
 *
 * @param status ID of the new status, like `PASSED`
 * @param description Why the status changes — the comment shown beside the new status
 */
fun ValidationRun.changeStatus(status: String, description: String? = null) {
    graphqlConnector.mutate(
        ChangeValidationRunStatusMutation(
            validationRunId = id.toInt(),
            validationRunStatusId = status,
            description = Optional.presentIfNotNull(description),
        )
    ) {
        it?.changeValidationRunStatus?.payloadUserErrors?.convert()
    }
}
