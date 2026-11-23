package net.nemerosa.ontrack.extension.scm.mock

import io.mockk.mockk
import net.nemerosa.ontrack.extension.scm.SCMExtensionConfigProperties
import net.nemerosa.ontrack.model.support.StorageService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MockSCMStoreConfigTest {

    private val config = MockSCMStoreConfig()
    private val storageService = mockk<StorageService>()

    @Test
    fun `Mock SCM keeps its repositories in memory by default`() {
        assertIs<InMemoryMockSCMStore>(store(SCMExtensionConfigProperties()))
    }

    @Test
    fun `Mock SCM is backed by the storage when persistent`() {
        val properties = SCMExtensionConfigProperties()
        properties.mock.persistent = true
        assertIs<StorageMockSCMStore>(store(properties))
    }

    @Test
    fun `The Git mirror of the mock SCM is off by default`() {
        val git = SCMExtensionConfigProperties().mock.git
        assertEquals("", git.pushUrl)
        assertEquals("", git.cloneUrl)
        assertEquals("", git.username)
        assertEquals("", git.password)
    }

    private fun store(properties: SCMExtensionConfigProperties): MockSCMStore =
        config.mockSCMStore(properties, storageService)

}
