package net.nemerosa.ontrack.extension.audittrail.model

import java.time.LocalDateTime

/**
 * The endorsement of an entry by the instance key: the Ed25519 signature of its hash (see
 * [TrailEndorsementFormat][net.nemerosa.ontrack.extension.audittrail.endorsement.TrailEndorsementFormat]).
 *
 * An entry written while the instance key was not provisioned has none.
 *
 * @property entryId ID of the endorsed entry
 * @property keyId ID of the key which endorsed it
 * @property signature Signature of the hash of the entry, in base64
 * @property time Server time of the endorsement, UTC, at the millisecond
 */
data class TrailEndorsement(
    val entryId: Int,
    val keyId: String,
    val signature: String,
    val time: LocalDateTime,
)
