package net.nemerosa.ontrack.extension.audittrail.endorsement

/**
 * A stored instance key which is not an Ed25519 private key in PKCS#8.
 */
class InstanceKeyFormatException(cause: Throwable?) : RuntimeException(
    "The instance key of the audit trail is not an Ed25519 private key in PKCS#8 (DER or PEM).",
    cause,
)
