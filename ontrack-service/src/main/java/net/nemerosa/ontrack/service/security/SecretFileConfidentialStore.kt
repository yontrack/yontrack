package net.nemerosa.ontrack.service.security

import net.nemerosa.ontrack.model.security.AbstractConfidentialStore
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.File

/**
 * Reads the keys directly from the files of a directory, never writes them: the encryption key,
 * which must be there, and any other key, such as the instance key of the audit trail, from the
 * file of the same name when it is there.
 *
 * This store is suitable for read-only secrets (stored in K8S secrets
 * mounted as volumes for example).
 */
@Component
@ConditionalOnProperty(name = [OntrackConfigProperties.KEY_STORE], havingValue = "secret", matchIfMissing = false)
class SecretFileConfidentialStore(
    ontrackConfigProperties: OntrackConfigProperties,
) : AbstractConfidentialStore() {

    private val key: ByteArray

    private val directory: File

    init {
        val path = ontrackConfigProperties.fileKeyStore.directory
        LoggerFactory.getLogger(SecretFileConfidentialStore::class.java).info(
            "[key-store] Using Secret based key store (directory = $path)"
        )
        if (path.isBlank()) {
            throw SecretFileConfidentialStoreNoDirectoryException()
        } else {
            val directory = File(path)
            if (!directory.exists() || !directory.isDirectory) {
                throw SecretFileConfidentialStoreInvalidDirectoryException(path)
            } else {
                val keyFile = File(directory, EncryptionServiceKeys.ENCRYPTION_KEY)
                if (!keyFile.exists() || !keyFile.isFile || !keyFile.canRead()) {
                    throw SecretFileConfidentialStoreMissingKeyFileException(keyFile)
                } else {
                    key = keyFile.readBytes()
                    this.directory = directory
                }
            }
        }
    }

    override fun store(key: String, payload: ByteArray) {
        throw SecretFileConfidentialStoreReadOnlyException(key)
    }

    /**
     * The encryption key, read at startup, or any other key from the file of the same name in the
     * directory, read at every call — so that a key mounted after the start, such as the instance
     * key of the audit trail, is found without a restart.
     */
    override fun load(key: String): ByteArray? =
        if (key == EncryptionServiceKeys.ENCRYPTION_KEY) {
            this.key
        } else if (KEY_NAME.matches(key)) {
            File(directory, key).takeIf { it.isFile && it.canRead() }?.readBytes()
        } else {
            null
        }

    companion object {
        /**
         * Names of the keys which are files of the directory: no path, no hidden file.
         */
        private val KEY_NAME = Regex("[A-Za-z0-9_-][A-Za-z0-9._-]*")
    }
}