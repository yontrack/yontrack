package net.nemerosa.ontrack.extension.audittrail.status

import net.nemerosa.ontrack.extension.audittrail.endorsement.InstanceKeyStatus
import net.nemerosa.ontrack.extension.audittrail.endorsement.InstancePublicKey
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageState
import java.time.LocalDateTime

/**
 * Status of the audit trail, for its administrators.
 *
 * @property licensed Whether the licence allows the audit trail
 * @property storage Status of the evidence storage
 * @property keyStatus Whether the instance key can endorse the entries
 * @property keys Public keys of the instance, empty while no key exists
 */
data class AuditTrailStatus(
    val licensed: Boolean,
    val storage: AuditTrailStorageStatus,
    val keyStatus: InstanceKeyStatus,
    val keys: List<InstancePublicKey>,
)

/**
 * Status and configuration of the evidence storage — never its credentials.
 *
 * @property state State of the storage
 * @property message Why the storage is not OK
 * @property checkedAt When the storage was last probed
 * @property endpoint Configured endpoint
 * @property bucket Configured bucket
 * @property region Configured region
 * @property pathStyle Whether the bucket is addressed path-style
 */
data class AuditTrailStorageStatus(
    val state: EvidenceStorageState,
    val message: String?,
    val checkedAt: LocalDateTime,
    val endpoint: String?,
    val bucket: String?,
    val region: String,
    val pathStyle: Boolean,
)
