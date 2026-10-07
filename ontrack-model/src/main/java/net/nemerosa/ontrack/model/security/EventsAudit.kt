package net.nemerosa.ontrack.model.security

/**
 * Right to read all the events of the instance, whatever the project ACLs.
 *
 * Granted to the Administrator role only.
 */
@CoreFunction
interface EventsAudit : GlobalFunction
