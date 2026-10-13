package net.nemerosa.ontrack.service.security

import io.mockk.mockk
import org.apache.commons.io.FileUtils
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EncryptionServiceTest {

    @Test
    fun `Encryption and decryption`() {
        // Temporary directory
        val dir = createTempDirectory().toFile()
        try {
            // Encryption service
            val encryptionService = EncryptionServiceImpl(
                mockk(relaxed = true),
                FileConfidentialStore(
                    dir
                ),
                InMemoryConfidentialKeyVersions(),
            )
            // Asserts master key file is created
            assertTrue(File(dir, "master.key").exists())
            // Encrypts a secret
            val encrypted = encryptionService.encrypt("verysecret")
            assertNotNull(encrypted) {
                assertTrue(it.isNotBlank())
            }
            // Asserts key file is created
            assertTrue(File(dir, "net.nemerosa.ontrack.security.EncryptionServiceImpl.encryption").exists())
            // Decryption
            val decrypted = encryptionService.decrypt(encrypted)
            assertEquals("verysecret", decrypted)
        } finally {
            FileUtils.deleteQuietly(dir)
        }
    }

}
