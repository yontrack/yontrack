package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyService
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstancePublicKey
import net.nemerosa.ontrack.extension.audittrail.status.AuditTrailStatus
import net.nemerosa.ontrack.extension.audittrail.status.AuditTrailStatusService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageState
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.stereotype.Controller

/**
 * GraphQL root fields of the audit trail.
 */
@Controller
class AuditTrailGraphQLController(
    private val instanceKeyService: InstanceKeyService,
    private val auditTrailStatusService: AuditTrailStatusService,
    private val evidenceStorageService: EvidenceStorageService,
) {

    /**
     * Public keys of the instance, as on `GET /rest/extension/audit-trail/keys`.
     */
    @QueryMapping
    fun auditTrailKeys(): List<InstancePublicKey> = instanceKeyService.getPublicKeys()

    /**
     * Status of the audit trail, for the administrators.
     */
    @QueryMapping
    fun auditTrailStatus(): AuditTrailStatus = auditTrailStatusService.status

    /**
     * State of the evidence storage, for every user: the evidence cell of the validation run page
     * says when evidence cannot be uploaded nor downloaded. Its state only — its configuration and
     * the reason of a failure are for the administrators, on [auditTrailStatus].
     */
    @QueryMapping
    fun auditTrailStorageState(): EvidenceStorageState = evidenceStorageService.status.state
}
