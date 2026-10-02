package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.model.events.Event

/**
 * Maps the events of some types to the entries they write.
 */
interface TrailEventMapper {

    /**
     * IDs of the types of the events this mapper maps. An event type is mapped by one mapper only.
     */
    val eventTypes: Set<String>

    /**
     * Entries written for an event, in the transaction of the change it records.
     *
     * Called as administrator: what the event does not carry is read whatever the rights of the
     * actor.
     *
     * @param event Event of one of the [eventTypes]
     * @return Entries to append, in order — none when the event changed no trail
     */
    fun map(event: Event): List<TrailEntryRequest>
}
