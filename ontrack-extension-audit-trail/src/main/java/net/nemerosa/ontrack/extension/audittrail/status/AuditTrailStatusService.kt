package net.nemerosa.ontrack.extension.audittrail.status

/**
 * Status of the audit trail: its licence, its evidence storage and its instance key.
 */
interface AuditTrailStatusService {

    /**
     * Status of the audit trail. Requires the global settings.
     */
    val status: AuditTrailStatus
}
