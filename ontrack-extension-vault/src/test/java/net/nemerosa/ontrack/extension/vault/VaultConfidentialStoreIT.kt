package net.nemerosa.ontrack.extension.vault

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.model.security.ConfidentialStore
import net.nemerosa.ontrack.test.TestUtils.uid
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import org.springframework.vault.core.VaultKeyValueOperationsSupport
import org.springframework.vault.core.VaultOperations
import java.nio.charset.Charset
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@TestPropertySource(
        properties = [
            "ontrack.config.key-store=vault",
        ]
)
@DirtiesContext
class VaultConfidentialStoreIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var store: ConfidentialStore

    @Autowired
    private lateinit var vaultOperations: VaultOperations

    @Autowired
    private lateinit var configProperties: VaultConfigProperties

    private fun kvOps() =
        vaultOperations.opsForKeyValue("/secret/data", VaultKeyValueOperationsSupport.KeyValueBackend.unversioned())

    @Test
    fun `Check Vault store is loaded`() {
        assertIs<VaultConfidentialStore>(store) {}
    }

    @Test
    fun `Storing and retrieving a key`() {
        val id = uid("K")
        store.store(id, KEY_BYTES)
        // Retriving the key
        val bytes = store.load(id)
        assertNotNull(bytes) {
            assertEquals(KEY_BYTES.toTypedArray().toList(), it.toTypedArray().toList())
        }
    }

    @Test
    fun `A key is stored in Vault as its payload in Base64`() {
        val id = uid("K")
        store.store(id, KEY_BYTES)
        val raw = kvOps().get("${configProperties.prefix}/$id")?.data
        assertEquals(
            mapOf("payload" to Base64.getEncoder().encodeToString(KEY_BYTES)),
            raw?.get("data")
        )
    }

    @Test
    fun `A key stored by Yontrack 5 is read back`() {
        val id = uid("K")
        kvOps().put(
            "${configProperties.prefix}/$id",
            mapOf("data" to mapOf("payload" to Base64.getEncoder().encodeToString(KEY_BYTES)))
        )
        val bytes = store.load(id)
        assertNotNull(bytes) {
            assertEquals(KEY_BYTES.toTypedArray().toList(), it.toTypedArray().toList())
        }
    }

    companion object {
        private val KEY_BYTES = "my-super-secret-key".toByteArray(Charset.forName("UTF-8"))
    }

}