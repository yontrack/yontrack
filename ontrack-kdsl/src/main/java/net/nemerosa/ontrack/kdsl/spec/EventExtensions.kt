package net.nemerosa.ontrack.kdsl.spec

import com.apollographql.apollo.api.Optional
import net.nemerosa.ontrack.kdsl.connector.Connected
import net.nemerosa.ontrack.kdsl.connector.Connector
import net.nemerosa.ontrack.kdsl.connector.graphql.GraphQLMissingDataException
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.EventsQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.EventFilterInput
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * An event of the instance, as the audit of the events returns it.
 *
 * @property id ID of the event
 * @property type ID of the type of the event, like `new_build`
 * @property time Time of the event (UTC)
 * @property user Name of the user who posted the event
 * @property message Message of the event, rendered as HTML
 * @property project Name of the project of the event, if any
 * @property values Values of the event, indexed by name
 */
data class Event(
    val id: Int,
    val type: String,
    val time: LocalDateTime,
    val user: String,
    val message: String,
    val project: String?,
    val values: Map<String, String>,
)

/**
 * Page of events, newest first.
 *
 * @property items Events of the page
 * @property nextOffset Offset of the next page, null when there is none
 */
data class EventPage(
    val items: List<Event>,
    val nextOffset: Int?,
)

/**
 * Audit of all the events of the instance, newest first. Requires the events audit function,
 * granted to the administrators.
 *
 * All the criteria are optional, and apply together.
 *
 * @param offset Offset of the page
 * @param size Size of the page, at most 100
 * @param from Events at or after this time (UTC, inclusive)
 * @param to Events at or before this time (UTC, inclusive)
 * @param user Case-insensitive prefix of the name of the user who posted the event
 * @param eventTypes IDs of the event types to keep - empty for all of them
 * @param project Name of a project, matching the event's project or its extra project
 */
fun Ontrack.events(
    offset: Int = 0,
    size: Int = 20,
    from: LocalDateTime? = null,
    to: LocalDateTime? = null,
    user: String? = null,
    eventTypes: List<String> = emptyList(),
    project: String? = null,
): EventPage {
    val page = graphqlConnector.query(
        EventsQuery(
            offset = offset,
            size = size,
            filter = Optional.present(
                EventFilterInput(
                    from = Optional.presentIfNotNull(from),
                    to = Optional.presentIfNotNull(to),
                    user = Optional.presentIfNotNull(user),
                    eventTypes = Optional.presentIfNotNull(eventTypes.takeIf { it.isNotEmpty() }),
                    project = Optional.presentIfNotNull(project),
                )
            ),
        )
    )?.events ?: throw GraphQLMissingDataException("Did not get back the events")
    return EventPage(
        items = page.pageItems.map {
            Event(
                id = it.id,
                type = it.eventType.id,
                time = it.time,
                user = it.user,
                message = it.message,
                project = it.project?.name,
                values = it.values.associate { nv -> nv.name to nv.value },
            )
        },
        nextOffset = page.pageInfo?.nextPage?.offset,
    )
}

/**
 * Format of an export of the events.
 *
 * @property id Value of the `format` parameter of the export
 */
enum class EventsExportFormat(val id: String) {
    CSV("csv"),
    JSON("json"),
}

/**
 * Management of the events of the instance.
 */
val Ontrack.events: EventsMgt get() = EventsMgt(connector)

/**
 * Management of the events of the instance.
 */
class EventsMgt(connector: Connector) : Connected(connector) {

    /**
     * Export of the events, newest first, as the file the events page downloads. Requires the
     * events audit function, granted to the administrators.
     *
     * All the criteria are optional, and apply together.
     *
     * @param format CSV or JSON
     * @param from Events at or after this time (UTC, inclusive)
     * @param to Events at or before this time (UTC, inclusive)
     * @param user Case-insensitive prefix of the name of the user who posted the event
     * @param eventTypes IDs of the event types to keep - empty for all of them
     * @param project Name of a project, matching the event's project or its extra project
     * @return Content of the file
     */
    fun export(
        format: EventsExportFormat,
        from: LocalDateTime? = null,
        to: LocalDateTime? = null,
        user: String? = null,
        eventTypes: List<String> = emptyList(),
        project: String? = null,
    ): String =
        connector.get(
            path = "/rest/admin/events/export",
            query = listOfNotNull(
                "format" to format.id,
                from?.let { "from" to it.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
                to?.let { "to" to it.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) },
                user?.let { "user" to it },
                eventTypes.takeIf { it.isNotEmpty() }?.let { "eventTypes" to it.joinToString(",") },
                project?.let { "project" to it },
            ).toMap(),
        ).body.asText()
}
