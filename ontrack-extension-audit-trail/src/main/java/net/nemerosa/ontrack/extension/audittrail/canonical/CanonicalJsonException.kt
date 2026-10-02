package net.nemerosa.ontrack.extension.audittrail.canonical

/**
 * A JSON tree outside the subset of RFC 8785 accepted in a trail.
 */
class CanonicalJsonException(message: String) : IllegalArgumentException(message)
