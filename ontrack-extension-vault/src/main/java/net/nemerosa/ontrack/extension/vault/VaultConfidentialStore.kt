package net.nemerosa.ontrack.extension.vault

import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseOrNull
import net.nemerosa.ontrack.model.security.AbstractConfidentialStore
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.apache.commons.lang3.Validate
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.vault.core.VaultKeyValueOperationsSupport
import org.springframework.vault.core.VaultOperations
import java.util.*

@Component
@ConditionalOnProperty(name = [OntrackConfigProperties.KEY_STORE], havingValue = VaultExtensionFeature.VAULT_KEY_STORE_PROPERTY)
class VaultConfidentialStore(
        private val vaultOperations: VaultOperations,
        private val configProperties: VaultConfigProperties
) : AbstractConfidentialStore() {

    private val kvPath = "/secret/data"

    private fun kvOps() = vaultOperations.opsForKeyValue(kvPath, VaultKeyValueOperationsSupport.KeyValueBackend.unversioned())

    /*
     * Vault is given plain maps, and what it gives back is read with the Yontrack mapper here rather
     * than with the one of Spring Vault, so that the stored secret keeps its 5.x shape,
     * `{"data": {"payload": "<Base64>"}}`, whatever the mapper configuration of Spring Vault.
     */

    override fun store(key: String, payload: ByteArray) {
        Validate.notNull(payload, "Key payload must not be null")
        val secret = mapOf("data" to mapOf("payload" to Base64.getEncoder().encodeToString(payload)))
        kvOps().put("${configProperties.prefix}/$key", secret)
    }

    override fun load(key: String): ByteArray? {
        val secret = kvOps().get("${configProperties.prefix}/$key")?.data ?: return null
        // Next to `data`, Vault answers with `metadata`, which is not part of the payload
        return secret["data"]?.asJson()?.parseOrNull<Key>()?.payload
    }

    init {
        LoggerFactory.getLogger(VaultConfidentialStore::class.java).info(
                "[key-store] Using Vault store at {}",
                configProperties.uri
        )
    }
}