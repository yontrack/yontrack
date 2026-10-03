package net.nemerosa.ontrack.extension.audittrail.storage

import net.nemerosa.ontrack.extension.audittrail.AbstractAuditTrailITSupport
import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The evidence storage against the MinIO of the integration test stack, to which the
 * `ontrack.extension.audit-trail.storage.*` properties of the stack point.
 */
class EvidenceStorageServiceIT : AbstractAuditTrailITSupport() {

    @Autowired
    private lateinit var auditTrailConfigProperties: AuditTrailConfigProperties

    @Autowired
    private lateinit var evidenceStorageService: EvidenceStorageService

    /**
     * A storage service for the configuration of the stack, changed by [code].
     */
    private fun <T> withStorage(
        code: AuditTrailConfigProperties.StorageProperties.() -> Unit,
        test: (EvidenceStorageService) -> T,
    ): T {
        val stack = auditTrailConfigProperties.storage
        val properties = AuditTrailConfigProperties().apply {
            storage.endpoint = stack.endpoint
            storage.bucket = stack.bucket
            storage.region = stack.region
            storage.pathStyle = stack.pathStyle
            storage.accessKey = stack.accessKey
            storage.secretKey = stack.secretKey
            storage.code()
        }
        val service = EvidenceStorageServiceImpl(properties)
        return try {
            test(service)
        } finally {
            service.destroy()
        }
    }

    @Test
    fun `Storage of the stack is configured`() {
        val storage = auditTrailConfigProperties.storage
        assertEquals(emptyList(), storage.missingProperties())
        assertEquals("yontrack-audit-trail", storage.bucket)
        assertTrue(storage.pathStyle, "MinIO is addressed path-style")
    }

    @Test
    fun `Storage OK`() {
        val status = evidenceStorageService.checkStatus()
        assertEquals(EvidenceStorageState.OK, status.state)
        assertNull(status.message)
        assertEquals(status, evidenceStorageService.status, "Status is cached")
        assertNotNull(evidenceStorageService.client)
    }

    @Test
    fun `Storage unreachable when its bucket does not exist`() {
        withStorage({ bucket = "yontrack-no-such-bucket" }) { service ->
            val status = service.checkStatus()
            assertEquals(EvidenceStorageState.UNREACHABLE, status.state)
            assertEquals("The bucket yontrack-no-such-bucket does not exist.", status.message)
        }
    }

    @Test
    fun `Storage unreachable when its credentials are refused`() {
        withStorage({ secretKey = "not-the-secret-key" }) { service ->
            val status = service.checkStatus()
            assertEquals(EvidenceStorageState.UNREACHABLE, status.state)
            assertEquals(
                "Access to the bucket yontrack-audit-trail is denied (HTTP 403): check the credentials.",
                status.message
            )
        }
    }

    @Test
    fun `Storage unreachable when nothing listens at its endpoint`() {
        // Port 1 (tcpmux) is reserved and never open on a development machine nor a CI runner
        withStorage({ endpoint = "http://localhost:1" }) { service ->
            val status = service.checkStatus()
            assertEquals(EvidenceStorageState.UNREACHABLE, status.state)
            val message = assertNotNull(status.message)
            assertTrue(
                message.startsWith("The storage cannot be reached at http://localhost:1: "),
                "Unreachable endpoint: $message"
            )
        }
    }

    @Test
    fun `Storage not configured`() {
        withStorage({ endpoint = null }) { service ->
            val status = service.checkStatus()
            assertEquals(EvidenceStorageState.NOT_CONFIGURED, status.state)
            assertEquals("Missing properties: ontrack.extension.audit-trail.storage.{endpoint}", status.message)
            assertNull(service.client)
        }
    }
}
