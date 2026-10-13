package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.it.QueryCounter
import net.nemerosa.ontrack.model.security.ConfidentialStore
import net.nemerosa.ontrack.model.support.ReadMemo
import net.nemerosa.ontrack.model.support.StorageService
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.BrokenBarrierException
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals

/**
 * One encryption key for all the replicas (#2086): each [CryptoConfidentialKey] plays the key of
 * one replica, all of them on the `jdbc` store of the database, under a key ID of their own.
 *
 * A key is generated, and imported, in transactions of its own: these tests are not transactional.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CryptoConfidentialKeyIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var storageService: StorageService

    @Autowired
    private lateinit var queryCounter: QueryCounter

    @Autowired
    private lateinit var versions: ConfidentialKeyVersions

    private fun replica(store: ConfidentialStore, id: String) = CryptoConfidentialKey(store, id, versions)

    private val jdbcStore: ConfidentialStore get() = JdbcConfidentialStore(storageService)

    @Test
    fun `two concurrent generations end with one key, used by both`() {
        val id = uid("key-")
        val store = FindingNoKeyTogether(jdbcStore)
        val a = replica(store, id)
        val b = replica(store, id)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val (byA, byB) = listOf(a, b)
                .map { key -> executor.submit<String> { key.encrypt("secret") } }
                .map { it.get(1, TimeUnit.MINUTES) }
            assertEquals("secret", b.decrypt(byA), "Encrypted by A, decrypted by B")
            assertEquals("secret", a.decrypt(byB), "Encrypted by B, decrypted by A")
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `a key imported through one replica is used by the other for its next encryption and decryption`() {
        val id = uid("key-")
        val a = replica(jdbcStore, id)
        val b = replica(jdbcStore, id)
        // Both use the generated key
        assertEquals("before", b.decrypt(a.encrypt("before")))

        val imported = randomKey()
        a.importKey(Base64.getEncoder().encodeToString(imported))

        val byB = b.encrypt("encrypted by B")
        assertEquals("encrypted by B", decrypt(imported, byB), "B encrypts with the imported key")
        assertEquals("encrypted by B", a.decrypt(byB), "Encrypted by B, decrypted by A")
        assertEquals("encrypted by A", b.decrypt(a.encrypt("encrypted by A")), "Encrypted by A, decrypted by B")
    }

    @Test
    fun `a key stored before the key versions is kept`() {
        val id = uid("key-")
        val payload = randomKey()
        jdbcStore.store(id, payload)

        val key = replica(jdbcStore, id)

        assertEquals("secret", decrypt(payload, key.encrypt("secret")))
    }

    @Test
    fun `the version of the key is read once per unit of work, and on each use outside of one`() {
        val id = uid("key-")
        val key = replica(jdbcStore, id)
        key.encrypt("generated")

        val inUnit = queryCounter.record {
            ReadMemo.unitOfWork {
                repeat(3) { key.decrypt(key.encrypt("secret")) }
            }
        }
        assertEquals(1, inUnit.on("STORAGE").withParameter(id).size, "One query in a unit of work:\n$inUnit")

        val outsideUnit = queryCounter.record {
            repeat(3) { key.encrypt("secret") }
        }
        assertEquals(3, outsideUnit.on("STORAGE").withParameter(id).size, "One query per use outside a unit of work:\n$outsideUnit")
    }

    private fun randomKey() = ByteArray(256).also { SecureRandom().nextBytes(it) }

    /**
     * Decrypts with the given payload of a key, as [CryptoConfidentialKey] uses it.
     */
    private fun decrypt(payload: ByteArray, encrypted: String): String {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(payload, 0, 128 / 8, "AES"))
        return String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), Charsets.UTF_8)
    }

    /**
     * Store on which two replicas looking for a key at the same time both find none: the first
     * one finding none waits for the other one to find none as well - or, when the other one
     * cannot look for it meanwhile, gives up waiting after a while.
     */
    private class FindingNoKeyTogether(private val store: ConfidentialStore) : ConfidentialStore by store {

        private val barrier = CyclicBarrier(2)

        override fun load(key: String): ByteArray? {
            val payload = store.load(key)
            if (payload == null) {
                try {
                    barrier.await(2, TimeUnit.SECONDS)
                } catch (_: TimeoutException) {
                    // The other replica could not look for the key meanwhile
                } catch (_: BrokenBarrierException) {
                    // Given up already
                }
            }
            return payload
        }
    }
}
