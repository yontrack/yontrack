package net.nemerosa.ontrack.extension.audittrail.endorsement

import net.nemerosa.ontrack.model.security.ConfidentialStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class InstanceKeyServiceImpl(
    private val confidentialStore: ConfidentialStore,
) : InstanceKeyService {

    private val logger = LoggerFactory.getLogger(InstanceKeyServiceImpl::class.java)

    /**
     * Time before the store is asked again for a key it could not give — so that a missing key
     * does not cost a call to the store (Vault, for example) on every entry.
     */
    internal var retryInterval: Duration = Duration.ofMinutes(1)

    @Volatile
    private var key: InstanceKey? = null

    /**
     * `System.nanoTime` of the last time the key could not be obtained
     */
    @Volatile
    private var failedAt: Long? = null

    override val keyStatus: InstanceKeyStatus
        get() = if (obtainKey() != null) InstanceKeyStatus.OK else InstanceKeyStatus.NOT_PROVISIONED

    override fun getPublicKeys(): List<InstancePublicKey> {
        val key = this.key ?: synchronized(this) {
            this.key ?: try {
                loadKey()?.also { this.key = it }
            } catch (e: Exception) {
                logger.error("[audit-trail] The instance key cannot be read: ${e.message}", e)
                null
            }
        }
        return listOfNotNull(key).map {
            InstancePublicKey(keyId = it.keyId, algorithm = it.algorithm, publicKey = it.publicKeyPem)
        }
    }

    override fun endorse(hash: String): Endorsement? =
        obtainKey()?.let { key ->
            Endorsement(keyId = key.keyId, signature = key.endorse(hash))
        }

    private fun obtainKey(): InstanceKey? {
        key?.let { return it }
        synchronized(this) {
            key?.let { return it }
            val failedAt = this.failedAt
            if (failedAt != null && System.nanoTime() - failedAt < retryInterval.toNanos()) {
                return null
            }
            val obtained = try {
                loadKey() ?: generateKey()
            } catch (e: Exception) {
                logger.error(
                    "[audit-trail] The instance key cannot be obtained, entries are not endorsed: ${e.message}",
                    e
                )
                null
            }
            if (obtained != null) {
                this.key = obtained
                this.failedAt = null
            } else {
                this.failedAt = System.nanoTime()
            }
            return obtained
        }
    }

    private fun loadKey(): InstanceKey? =
        confidentialStore.load(STORE_KEY)?.let { InstanceKey.parse(it) }

    /**
     * Generates and stores a key, then reads it back: when several instances generate one at the
     * same time, each uses the one which was stored last.
     */
    private fun generateKey(): InstanceKey? {
        val generated = InstanceKey.generate()
        try {
            confidentialStore.store(STORE_KEY, generated.toPkcs8())
        } catch (e: Exception) {
            logger.error(
                "[audit-trail] No instance key is provisioned under $STORE_KEY and the key store refuses to " +
                        "store one: entries are not endorsed until a key is provisioned (${e.message})"
            )
            return null
        }
        val key = loadKey() ?: generated
        logger.info("[audit-trail] Instance key generated: ${key.keyId}")
        return key
    }

    companion object {
        /**
         * Key of the instance key in the confidential store
         */
        const val STORE_KEY = "audit-trail.ed25519"
    }
}
