package net.nemerosa.ontrack.extension.av.security

import net.nemerosa.ontrack.model.security.ProjectFunction

/**
 * Rescheduling an auto-versioning order, checked on the **target** project of the order.
 *
 * Granted to the auto-versioning controllers, project or global, to the project owners and managers,
 * to the automation and to the administrators. Never to the participants, the agents or the
 * read-only tokens.
 */
interface AutoVersioningReschedule : ProjectFunction
