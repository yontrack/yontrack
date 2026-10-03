package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.extension.audittrail.AbstractAuditTrailITSupport
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageService
import net.nemerosa.ontrack.extension.audittrail.ui.EvidenceController
import net.nemerosa.ontrack.extension.audittrail.ui.EvidenceView
import net.nemerosa.ontrack.model.security.ValidationRunCreate
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.test.TestUtils.uid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockMultipartHttpServletRequest
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.security.MessageDigest
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Support for the integration tests of the evidences, against the MinIO of the integration test
 * stack.
 *
 * Every test uploads a content of its own: blobs are addressed by their content, and a content
 * shared between tests would share their blob.
 */
abstract class AbstractEvidenceITSupport : AbstractAuditTrailITSupport() {

    @Autowired
    protected lateinit var evidenceController: EvidenceController

    @Autowired
    protected lateinit var evidenceService: EvidenceService

    @Autowired
    protected lateinit var evidenceStorageService: EvidenceStorageService

    @Autowired
    protected lateinit var evidenceRepository: EvidenceRepository

    // Content

    protected fun pdf() = "%PDF-1.7\n% ${uid("pdf-")}\n".toByteArray()

    protected fun html() =
        "<!DOCTYPE html><html><script>alert(document.cookie)</script><!-- ${uid("h-")} --></html>".toByteArray()

    protected fun png() =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + uid("png-").toByteArray()

    /**
     * SHA-256 of a content, computed by the JDK, independently of the code under test.
     */
    protected fun sha256(content: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))

    // Storage

    protected val client get() = assertNotNull(evidenceStorageService.client, "Storage of the stack")

    protected fun blob(sha256: String): ByteArray? =
        try {
            client.s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(client.bucket).key("blobs/$sha256").build()
            ).asByteArray()
        } catch (_: NoSuchKeyException) {
            null
        }

    /**
     * Whether an object exists in the bucket.
     */
    protected fun exists(key: String): Boolean =
        try {
            client.s3.headObject(HeadObjectRequest.builder().bucket(client.bucket).key(key).build())
            true
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) false else throw e
        }

    protected fun uploads(): Int =
        client.s3.listObjectsV2(
            ListObjectsV2Request.builder().bucket(client.bucket).prefix("uploads/").build()
        ).keyCount()

    /**
     * Puts an object straight into the bucket, behind the back of the evidence service.
     */
    protected fun put(key: String, content: ByteArray) {
        client.s3.putObject(
            PutObjectRequest.builder().bucket(client.bucket).key(key).build(),
            RequestBody.fromBytes(content),
        )
    }

    protected fun deleteBlob(sha256: String) {
        client.s3.deleteObject(DeleteObjectRequest.builder().bucket(client.bucket).key("blobs/$sha256").build())
    }

    // Validation runs

    /**
     * A validation run, the first one of its build: `build.created` is seq 1 and `validation.run`
     * seq 2 of the trail of its build.
     */
    protected fun validationRun(code: ValidationRun.() -> Unit) {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        validate(vs).code()
                    }
                }
            }
        }
    }

    /**
     * Runs [code] as a user who can create validation runs on the project of the run, as CI does.
     */
    protected fun <T> ValidationRun.asCreator(code: () -> T): T =
        asUserWithView(this).withProjectFunction(this, ValidationRunCreate::class.java).call(code)

    protected fun request(
        content: ByteArray,
        fileName: String? = "report.pdf",
        partType: String? = "application/pdf",
        fields: Map<String, String> = emptyMap(),
    ) = MockMultipartHttpServletRequest().apply {
        addFile(MockMultipartFile(EvidenceController.PART_FILE, fileName, partType, content))
        fields.forEach { (name, value) -> addParameter(name, value) }
    }

    /**
     * Uploads an evidence through the REST end point, as a creator of validation runs.
     */
    protected fun ValidationRun.upload(
        content: ByteArray,
        fileName: String? = "report.pdf",
        partType: String? = "application/pdf",
        fields: Map<String, String> = emptyMap(),
    ): EvidenceView = asCreator {
        val response = evidenceController.upload(id(), request(content, fileName, partType, fields))
        assertEquals(201, response.statusCode.value())
        response.body!!
    }
}
