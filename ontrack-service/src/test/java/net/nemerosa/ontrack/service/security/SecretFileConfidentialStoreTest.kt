package net.nemerosa.ontrack.service.security

import io.mockk.mockk
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.io.path.*
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class SecretFileConfidentialStoreTest {

    @Test
    fun `Invalid if directory property is not set`() {
        val properties = OntrackConfigProperties()
        assertFailsWith<SecretFileConfidentialStoreNoDirectoryException> {
            SecretFileConfidentialStore(properties)
        }
    }

    @Test
    fun `Invalid if directory does not exist`() {
        val file = createTempDirectory()
        file.deleteExisting()
        val properties = OntrackConfigProperties().apply {
            fileKeyStore.directory = file.absolutePathString()
        }
        assertFailsWith<SecretFileConfidentialStoreInvalidDirectoryException> {
            SecretFileConfidentialStore(properties)
        }
    }

    @Test
    fun `Invalid if directory is not a directory`() {
        val file = createTempFile()
        val properties = OntrackConfigProperties().apply {
            fileKeyStore.directory = file.absolutePathString()
        }
        assertFailsWith<SecretFileConfidentialStoreInvalidDirectoryException> {
            SecretFileConfidentialStore(properties)
        }
    }

    @Test
    fun `Invalid if key file does not exist`() {
        val root = createTempDirectory()
        val properties = OntrackConfigProperties().apply {
            fileKeyStore.directory = root.absolutePathString()
        }
        assertFailsWith<SecretFileConfidentialStoreMissingKeyFileException> {
            SecretFileConfidentialStore(properties)
        }
    }

    @Test
    fun `Invalid if key file is not a file`() {
        val root = createTempDirectory()
        root.resolve(EncryptionServiceKeys.ENCRYPTION_KEY).createDirectory()
        val properties = OntrackConfigProperties().apply {
            fileKeyStore.directory = root.absolutePathString()
        }
        assertFailsWith<SecretFileConfidentialStoreMissingKeyFileException> {
            SecretFileConfidentialStore(properties)
        }
    }

    @Test
    fun `Valid key file`() {

        val sr = SecureRandom()
        val random = ByteArray(256)
        sr.nextBytes(random)

        val root = createTempDirectory()
        val keyFile = root.resolve(EncryptionServiceKeys.ENCRYPTION_KEY).createFile()
        keyFile.writeBytes(random)

        val properties = OntrackConfigProperties().apply {
            fileKeyStore.directory = root.absolutePathString()
        }
        val store = SecretFileConfidentialStore(properties)

        val encryptionService = EncryptionServiceImpl(mockk(relaxed = true), store)
        val plain = "some plain text"
        val encrypted = encryptionService.encrypt(plain)
        assertNotNull(encrypted) {
            assertTrue(it != plain, "Encrypted")
        }
        val decrypted = encryptionService.decrypt(encrypted)
        assertEquals(plain, decrypted, "Decrypted")
    }

    @Test
    fun `Other keys are read from the files of the directory, when they are mounted`() {
        val root = createTempDirectory()
        root.resolve(EncryptionServiceKeys.ENCRYPTION_KEY).writeBytes(ByteArray(256) { 1 })
        val properties = OntrackConfigProperties().apply {
            fileKeyStore.directory = root.absolutePathString()
        }
        val store = SecretFileConfidentialStore(properties)

        assertNull(store.load("audit-trail.ed25519"), "Key not mounted")

        val payload = "-----BEGIN PRIVATE KEY-----\n...\n".toByteArray()
        root.resolve("audit-trail.ed25519").writeBytes(payload)
        assertContentEquals(payload, store.load("audit-trail.ed25519"), "Key mounted after the start of the store")
    }

    @Test
    fun `Only the files of the directory are keys`() {
        val parent = createTempDirectory()
        val root = parent.resolve("keys").createDirectory()
        root.resolve(EncryptionServiceKeys.ENCRYPTION_KEY).writeBytes(ByteArray(256) { 1 })
        parent.resolve("outside").writeBytes("outside".toByteArray())
        root.resolve("sub").createDirectory()
        val properties = OntrackConfigProperties().apply {
            fileKeyStore.directory = root.absolutePathString()
        }
        val store = SecretFileConfidentialStore(properties)

        assertNull(store.load("../outside"))
        assertNull(store.load("sub"))
        assertNull(store.load(""))
    }

    @Test
    fun `The store is read-only`() {
        val root = createTempDirectory()
        root.resolve(EncryptionServiceKeys.ENCRYPTION_KEY).writeBytes(ByteArray(256) { 1 })
        val properties = OntrackConfigProperties().apply {
            fileKeyStore.directory = root.absolutePathString()
        }
        val store = SecretFileConfidentialStore(properties)
        assertFailsWith<SecretFileConfidentialStoreReadOnlyException> {
            store.store("audit-trail.ed25519", ByteArray(48))
        }
        assertNull(store.load("audit-trail.ed25519"))
    }

}