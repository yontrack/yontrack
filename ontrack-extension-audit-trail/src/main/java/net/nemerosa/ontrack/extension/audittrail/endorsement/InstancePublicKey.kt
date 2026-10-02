package net.nemerosa.ontrack.extension.audittrail.endorsement

/**
 * A public key of the instance, with which its endorsements are verified.
 *
 * @property keyId ID of the key, as named by the endorsements
 * @property algorithm Algorithm of the key: `Ed25519`
 * @property publicKey Public key, in PEM
 */
data class InstancePublicKey(
    val keyId: String,
    val algorithm: String,
    val publicKey: String,
)
