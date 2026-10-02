package net.nemerosa.ontrack.extension.audittrail.endorsement

/**
 * Endorsement of a hash by the instance key, before it is stored for an entry.
 *
 * @property keyId ID of the key which endorsed the hash
 * @property signature Signature of the hash, in base64 (see [TrailEndorsementFormat])
 */
data class Endorsement(
    val keyId: String,
    val signature: String,
)
