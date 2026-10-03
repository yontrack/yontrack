package net.nemerosa.ontrack.extension.audittrail.storage

import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The evidence storage without any S3 service behind it: the configuration alone. Reachability is
 * tested against the MinIO of the integration test stack, in [EvidenceStorageServiceIT].
 */
class EvidenceStorageServiceImplTest {

    private fun service(code: AuditTrailConfigProperties.StorageProperties.() -> Unit) =
        EvidenceStorageServiceImpl(
            AuditTrailConfigProperties().apply { storage.code() }
        )

    @Test
    fun `Not configured without any property`() {
        val status = service {}.status
        assertEquals(EvidenceStorageState.NOT_CONFIGURED, status.state)
        assertEquals(
            "Missing properties: ontrack.extension.audit-trail.storage.{endpoint, bucket, access-key, secret-key}",
            status.message
        )
        assertNotNull(status.checkedAt)
    }

    @Test
    fun `Not configured as long as a required property is missing`() {
        val status = service {
            endpoint = "http://localhost:9000"
            bucket = "evidence"
            accessKey = "key"
            secretKey = " "
        }.status
        assertEquals(EvidenceStorageState.NOT_CONFIGURED, status.state)
        assertEquals(
            "Missing properties: ontrack.extension.audit-trail.storage.{secret-key}",
            status.message
        )
    }

    @Test
    fun `No client without the configuration`() {
        assertNull(service {}.client)
    }

    @Test
    fun `Maximum size of 50 MB by default`() {
        assertEquals(50L * 1024 * 1024, AuditTrailConfigProperties().storage.maxSize.toBytes())
    }
}
