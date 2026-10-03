package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.model.exceptions.NotFoundException

/**
 * No evidence to read: none with this ID, or it is deleted, or its content is missing from the
 * storage.
 */
class EvidenceNotFoundException(message: String) : NotFoundException(message)
