package net.nemerosa.ontrack.extension.vault

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.vault.core.VaultKeyValueOperations
import org.springframework.vault.core.VaultKeyValueOperationsSupport
import org.springframework.vault.core.VaultOperations
import org.springframework.vault.support.VaultResponse
import java.nio.charset.Charset
import java.util.*
import kotlin.test.assertEquals

class VaultConfidentialStoreTest {

    private lateinit var vaultKvOps: VaultKeyValueOperations
    private lateinit var vaultOperations: VaultOperations

    @BeforeEach
    fun before() {
        vaultKvOps = mockk(relaxed = true)
        vaultOperations = mockk(relaxed = true)
        every {
            vaultOperations.opsForKeyValue("/secret/data", VaultKeyValueOperationsSupport.KeyValueBackend.unversioned())
        } returns vaultKvOps
    }

    @Test
    fun default_config() {
        val configProperties = VaultConfigProperties()
        val store = VaultConfidentialStore(
            vaultOperations,
            configProperties
        )

        store.store(KEY_NAME, KEY_BYTES)
        verify(exactly = 1) {
            vaultKvOps.put("ontrack/keys/$KEY_NAME", secret)
        }

        val response = VaultResponse()
        response.data = secret
        every {
            vaultKvOps.get("ontrack/keys/$KEY_NAME")
        } returns response
        val bytes = store.load(KEY_NAME)
        assertEquals(KEY_BYTES.toList(), bytes?.toList())
    }

    @Test
    fun custom_prefix() {
        val configProperties = VaultConfigProperties().apply {
            prefix = "custom/keys"
        }
        val store = VaultConfidentialStore(
            vaultOperations,
            configProperties
        )

        store.store(KEY_NAME, KEY_BYTES)
        verify(exactly = 1) {
            vaultKvOps.put("custom/keys/$KEY_NAME", secret)
        }

        val response = VaultResponse()
        response.data = secret
        every {
            vaultKvOps.get("custom/keys/$KEY_NAME")
        } returns response
        val bytes = store.load(KEY_NAME)
        assertEquals(KEY_BYTES.toList(), bytes?.toList())
    }

    companion object {
        private const val KEY_NAME = "my-key"
        private val KEY_BYTES = "my-super-secret-key".toByteArray(Charset.forName("UTF-8"))

        /**
         * The secret as stored in Vault, a plain map.
         */
        private val secret: Map<String, Any> =
            mapOf("data" to mapOf("payload" to Base64.getEncoder().encodeToString(KEY_BYTES)))
    }
}