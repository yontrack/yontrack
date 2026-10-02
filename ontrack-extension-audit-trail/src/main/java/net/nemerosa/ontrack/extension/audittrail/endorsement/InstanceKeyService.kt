package net.nemerosa.ontrack.extension.audittrail.endorsement

/**
 * The instance key, which endorses the entries of the trails.
 *
 * It lives in the [ConfidentialStore][net.nemerosa.ontrack.model.security.ConfidentialStore] under
 * `audit-trail.ed25519`, as an Ed25519 private key in PKCS#8, and is generated on first use when
 * the store has none. When the store has none and refuses to store one — the read-only `secret`
 * store — nothing is endorsed until a key is provisioned in it; the trail is written all the same.
 */
interface InstanceKeyService {

    /**
     * Whether the instance key can endorse the entries. Generates the key when the store has none.
     */
    val keyStatus: InstanceKeyStatus

    /**
     * Public keys of the instance — its key, when there is one. Never generates the key.
     *
     * @return Public keys, empty when there is no key yet
     */
    fun getPublicKeys(): List<InstancePublicKey>

    /**
     * Endorses the hash of an entry, generating the key on first use.
     *
     * @param hash Hash of the entry, 64 lowercase hex characters
     * @return Endorsement, or `null` when the key is not provisioned
     */
    fun endorse(hash: String): Endorsement?
}
