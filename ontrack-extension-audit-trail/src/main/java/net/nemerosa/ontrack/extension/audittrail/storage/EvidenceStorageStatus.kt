package net.nemerosa.ontrack.extension.audittrail.storage

import java.time.LocalDateTime

/**
 * Status of the evidence storage, as its last probe found it.
 *
 * @property state State of the storage
 * @property message Why the storage is not OK, for the administrators — `null` when it is OK
 * @property checkedAt When the storage was probed
 */
data class EvidenceStorageStatus(
    val state: EvidenceStorageState,
    val message: String?,
    val checkedAt: LocalDateTime,
)
