package net.nemerosa.ontrack.model.events

import net.nemerosa.ontrack.common.api.APIDescription
import java.time.LocalDateTime

/**
 * Filter on the events of the instance. Every criterion is optional, and they are combined.
 *
 * @property from Events at or after this time (UTC, inclusive)
 * @property to Events at or before this time (UTC, inclusive)
 * @property user Case-insensitive prefix of the name of the user who posted the event
 * @property eventTypes IDs of the event types to keep - empty or null for all the types
 * @property project Name of a project, matching the event's project or its extra project
 */
@APIDescription("Filter on the events of the instance. Every criterion is optional, and they are combined.")
data class EventFilter(
    @APIDescription("Events at or after this time (UTC, inclusive)")
    val from: LocalDateTime? = null,
    @APIDescription("Events at or before this time (UTC, inclusive)")
    val to: LocalDateTime? = null,
    @APIDescription("Case-insensitive prefix of the name of the user who posted the event")
    val user: String? = null,
    @APIDescription("IDs of the event types to keep - empty or absent for all the types")
    val eventTypes: List<String>? = null,
    @APIDescription("Name of a project, matching the event's project or its extra project. An unknown name returns no event.")
    val project: String? = null,
)
