package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.model.events.Event

/**
 * A value of the event, `null` when it is not set.
 */
internal fun Event.value(name: String): String? = values[name]?.value
