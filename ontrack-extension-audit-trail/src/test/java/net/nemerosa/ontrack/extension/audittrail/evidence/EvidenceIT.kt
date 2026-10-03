package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.extension.audittrail.AbstractAuditTrailITSupport
import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import net.nemerosa.ontrack.extension.audittrail.events.AuditTrailEvents
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageServiceImpl
import net.nemerosa.ontrack.extension.audittrail.ui.EvidenceController
import net.nemerosa.ontrack.extension.audittrail.ui.EvidenceView
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerificationService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.security.ValidationRunCreate
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockMultipartHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.util.unit.DataSize
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.security.MessageDigest
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Upload, listing, download and verification of the evidences, against the MinIO of the
 * integration test stack.
 *
 * Every test uploads a content of its own: blobs are addressed by their content, and a content
 * shared between tests would share their blob.
 */
class EvidenceIT : AbstractAuditTrailITSupport() {

    @Autowired
    private lateinit var evidenceController: EvidenceController

    @Autowired
    private lateinit var evidenceService: EvidenceService

    @Autowired
    private lateinit var evidenceStorageService: EvidenceStorageService

    @Autowired
    private lateinit var evidenceRepository: EvidenceRepository

    @Autowired
    private lateinit var auditTrailConfigProperties: AuditTrailConfigProperties

    @Autowired
    private lateinit var auditTrailLicense: AuditTrailLicense

    @Autowired
    private lateinit var trailService: TrailService

    @Autowired
    private lateinit var trailVerificationService: TrailVerificationService

    @Autowired
    private lateinit var eventPostService: EventPostService

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    // Content

    private fun pdf() = "%PDF-1.7\n% ${uid("pdf-")}\n".toByteArray()

    private fun html() = "<!DOCTYPE html><html><script>alert(document.cookie)</script><!-- ${uid("h-")} --></html>".toByteArray()

    private fun png() = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + uid("png-").toByteArray()

    /**
     * SHA-256 of a content, computed by the JDK, independently of the code under test.
     */
    private fun sha256(content: ByteArray): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))

    // Storage

    private val client get() = assertNotNull(evidenceStorageService.client, "Storage of the stack")

    private fun blob(sha256: String): ByteArray? =
        try {
            client.s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(client.bucket).key("blobs/$sha256").build()
            ).asByteArray()
        } catch (_: NoSuchKeyException) {
            null
        }

    private fun uploads(): Int =
        client.s3.listObjectsV2(
            ListObjectsV2Request.builder().bucket(client.bucket).prefix("uploads/").build()
        ).keyCount()

    // Validation runs

    /**
     * A validation run, the first one of its build: `build.created` is seq 1 and `validation.run`
     * seq 2 of the trail of its build.
     */
    private fun validationRun(code: ValidationRun.() -> Unit) {
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
    private fun <T> ValidationRun.asCreator(code: () -> T): T =
        asUserWithView(this).withProjectFunction(this, ValidationRunCreate::class.java).call(code)

    private fun request(
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
    private fun ValidationRun.upload(
        content: ByteArray,
        fileName: String? = "report.pdf",
        partType: String? = "application/pdf",
        fields: Map<String, String> = emptyMap(),
    ): EvidenceView = asCreator {
        val response = evidenceController.upload(id(), request(content, fileName, partType, fields))
        assertEquals(201, response.statusCode.value())
        response.body!!
    }

    private fun ValidationRun.refused(error: EvidenceError, content: ByteArray, fields: Map<String, String> = emptyMap()) {
        val ex = assertThrows<EvidenceException> {
            asCreator { evidenceController.upload(id(), request(content, fields = fields)) }
        }
        assertEquals(error, ex.error)
        // Answered with its status and code
        val response = evidenceController.onEvidenceException(ex)
        assertEquals(error.status, response.statusCode.value())
        assertEquals(error.code, response.body?.code)
    }

    // Download

    private class Download(val headers: HttpHeaders, val content: ByteArray)

    private fun download(id: Int, controller: EvidenceController = evidenceController): Download {
        val response = MockHttpServletResponse()
        controller.download(id, response)
        assertEquals(200, response.status)
        val headers = HttpHeaders()
        response.headerNames.forEach { name -> headers.addAll(name, response.getHeaders(name)) }
        return Download(headers, response.contentAsByteArray)
    }

    /**
     * Downloads as a user who can only see the validation run.
     */
    private fun ValidationRun.downloadAsViewer(id: Int): Download = asUserWithView(this).call { download(id) }

    // Upload

    @Test
    fun `Upload stores the content under its SHA-256 and returns the evidence`() {
        validationRun {
            val content = pdf()
            val evidence = upload(content)
            assertEquals(id(), evidence.validationRunId)
            assertEquals("report.pdf", evidence.fileName)
            assertEquals("application/pdf", evidence.mediaType)
            assertEquals(content.size.toLong(), evidence.size)
            assertEquals(sha256(content), evidence.sha256)
            assertNull(evidence.source)
            assertNull(evidence.externalDigest)
            assertNull(evidence.deletedAt)
            assertEquals("/rest/extension/audit-trail/evidence/${evidence.id}/download", evidence.downloadUrl)
            assertTrue(content.contentEquals(blob(sha256(content))), "Content stored under blobs/<sha256>")
        }
    }

    @Test
    fun `Upload leaves no upload in progress behind`() {
        validationRun {
            val before = uploads()
            upload(pdf())
            assertEquals(before, uploads())
        }
    }

    @Test
    fun `Upload with a source and a matching external digest`() {
        validationRun {
            val content = pdf()
            val evidence = upload(
                content,
                fields = mapOf(
                    "sourceTool" to "trivy",
                    "sourceVersion" to "0.58.1",
                    "sourceUrl" to "https://ci.example.com/job/1",
                    "externalDigest" to "sha256:${sha256(content).uppercase()}",
                ),
            )
            assertEquals(EvidenceSource(tool = "trivy", version = "0.58.1", url = "https://ci.example.com/job/1"), evidence.source)
            assertEquals(sha256(content), evidence.externalDigest)
        }
    }

    @Test
    fun `Fields take precedence over the part for the file name and the media type`() {
        validationRun {
            val evidence = upload(
                pdf(),
                fileName = "upload.bin",
                partType = "application/octet-stream",
                fields = mapOf("fileName" to "trivy.pdf", "mediaType" to "application/pdf"),
            )
            assertEquals("trivy.pdf", evidence.fileName)
            assertEquals("application/pdf", evidence.mediaType)
        }
    }

    @Test
    fun `A file name is a label, never a path`() {
        validationRun {
            val content = pdf()
            val evidence = upload(content, fileName = "../../../etc/passwd\r\nX-Injected: 1")
            assertEquals("passwdX-Injected: 1", evidence.fileName)
            // The key of the content is its SHA-256 alone
            assertTrue(content.contentEquals(blob(sha256(content))))
        }
    }

    @Test
    fun `Upload without a media type is application octet-stream`() {
        validationRun {
            val evidence = upload(pdf(), partType = null)
            assertEquals("application/octet-stream", evidence.mediaType)
        }
    }

    @Test
    fun `Upload appends evidence attached to the trail of the build`() {
        validationRun {
            val content = pdf()
            val evidence = upload(
                content,
                fields = mapOf("sourceTool" to "trivy", "externalDigest" to sha256(content)),
            )
            val entry = asAdmin { trailService.getEntries(build) }.last()
            assertEquals(3, entry.seq)
            assertEquals(TrailEntryTypes.EVIDENCE_ATTACHED, entry.type)
            assertEquals(
                mapOf(
                    "validationStamp" to mapOf("id" to validationStamp.id(), "name" to validationStamp.name),
                    "validationRun" to mapOf("id" to id(), "order" to 1),
                    "evidence" to mapOf(
                        "id" to evidence.id,
                        "fileName" to "report.pdf",
                        "mediaType" to "application/pdf",
                        "size" to content.size,
                        "sha256" to sha256(content),
                        "source" to mapOf("tool" to "trivy"),
                        "externalDigest" to sha256(content),
                    ),
                ).asJson(),
                entry.payload,
            )
            assertEquals(evidence.collectedBy, entry.actor)
            assertEquals("ui", entry.actor.path("via").asString())
        }
    }

    @Test
    fun `Upload posts evidence attached`() {
        validationRun {
            val content = pdf()
            val evidence = upload(content)
            val event = assertNotNull(asAdmin { eventQueryService.getLastEvent(build, AuditTrailEvents.EVIDENCE_ATTACHED) })
            assertEquals(evidence.id.toString(), event.getValue(AuditTrailEvents.EVENT_EVIDENCE_ID))
            assertEquals("report.pdf", event.getValue(AuditTrailEvents.EVENT_EVIDENCE_FILE_NAME))
            assertEquals("application/pdf", event.getValue(AuditTrailEvents.EVENT_EVIDENCE_MEDIA_TYPE))
            assertEquals(content.size.toString(), event.getValue(AuditTrailEvents.EVENT_EVIDENCE_SIZE))
            assertEquals(sha256(content), event.getValue(AuditTrailEvents.EVENT_EVIDENCE_SHA256))
        }
    }

    @Test
    fun `The same content uploaded twice is two evidences sharing one blob`() {
        validationRun {
            val content = pdf()
            val first = upload(content)
            val second = upload(content, fileName = "again.pdf")
            assertTrue(first.id != second.id)
            assertEquals(first.sha256, second.sha256)
            assertEquals(listOf(first.id, second.id), asUserWithView(this).call { evidenceService.getEvidences(this) }.map { it.id })
        }
    }

    // GraphQL

    @Test
    fun `Evidences of a validation run in GraphQL`() {
        validationRun {
            val content = pdf()
            val evidence = upload(content, fields = mapOf("sourceTool" to "trivy", "sourceUrl" to "https://ci.example.com/job/1"))
            asUserWithView(this).call {
                run(
                    """
                        query (${'$'}id: Int!) {
                            validationRuns(id: ${'$'}id) {
                                evidence {
                                    id
                                    validationRunId
                                    fileName
                                    mediaType
                                    size
                                    sha256
                                    collectedAt
                                    collectedBy
                                    source { tool version url }
                                    externalDigest
                                    deletedAt
                                    downloadUrl
                                }
                            }
                        }
                    """,
                    mapOf("id" to id()),
                ) { data ->
                    val node = data.path("validationRuns").single().path("evidence").single()
                    assertEquals(evidence.id, node.path("id").asInt())
                    assertEquals(id(), node.path("validationRunId").asInt())
                    assertEquals("report.pdf", node.path("fileName").asString())
                    assertEquals("application/pdf", node.path("mediaType").asString())
                    assertEquals(content.size.toLong(), node.path("size").asLong())
                    assertEquals(sha256(content), node.path("sha256").asString())
                    assertTrue(node.path("collectedAt").asString().isNotBlank())
                    assertEquals(evidence.collectedBy, node.path("collectedBy"))
                    assertEquals(
                        mapOf("tool" to "trivy", "version" to null, "url" to "https://ci.example.com/job/1").asJson(),
                        node.path("source"),
                    )
                    assertTrue(node.path("externalDigest").isNull)
                    assertTrue(node.path("deletedAt").isNull)
                    assertEquals("/rest/extension/audit-trail/evidence/${evidence.id}/download", node.path("downloadUrl").asString())
                }
            }
        }
    }

    @Test
    fun `No evidence for a validation run in GraphQL`() {
        validationRun {
            asUserWithView(this).call {
                run("""{ validationRuns(id: ${id()}) { evidence { id } } }""") { data ->
                    assertEquals(0, data.path("validationRuns").single().path("evidence").size())
                }
            }
        }
    }

    // Refusals

    @Test
    fun `Upload refused when the external digest is not the one of the content`() {
        validationRun {
            val content = pdf()
            val before = uploads()
            refused(EvidenceError.DIGEST_MISMATCH, content, fields = mapOf("externalDigest" to sha256("other".toByteArray())))
            assertNull(blob(sha256(content)), "No blob for a refused evidence")
            assertEquals(before, uploads(), "Upload deleted")
            assertEquals(emptyList(), asAdmin { evidenceService.getEvidences(this) })
            assertEquals(2, asAdmin { trailService.getEntries(build) }.size, "No entry")
        }
    }

    @Test
    fun `Upload refused when bigger than the maximum size`() {
        validationRun {
            val content = pdf()
            val maxSize = auditTrailConfigProperties.storage.maxSize
            val before = uploads()
            auditTrailConfigProperties.storage.maxSize = DataSize.ofBytes(content.size - 1L)
            try {
                refused(EvidenceError.TOO_LARGE, content)
            } finally {
                auditTrailConfigProperties.storage.maxSize = maxSize
            }
            assertNull(blob(sha256(content)))
            assertEquals(before, uploads())
            assertEquals(emptyList(), asAdmin { evidenceService.getEvidences(this) })
        }
    }

    @Test
    fun `Upload of the maximum size accepted`() {
        validationRun {
            val content = pdf()
            val maxSize = auditTrailConfigProperties.storage.maxSize
            auditTrailConfigProperties.storage.maxSize = DataSize.ofBytes(content.size.toLong())
            try {
                upload(content)
            } finally {
                auditTrailConfigProperties.storage.maxSize = maxSize
            }
            assertTrue(content.contentEquals(blob(sha256(content))))
        }
    }

    @Test
    fun `Upload refused for an invalid external digest`() {
        validationRun {
            refused(EvidenceError.INVALID, pdf(), fields = mapOf("externalDigest" to "md5:098f6bcd4621d373cade4e832627b4f6"))
        }
    }

    @Test
    fun `Upload refused for a source URL which is not an HTTP one`() {
        validationRun {
            refused(EvidenceError.INVALID, pdf(), fields = mapOf("sourceUrl" to "javascript:alert(1)"))
        }
    }

    @Test
    fun `Upload refused for a wildcard media type`() {
        validationRun {
            refused(EvidenceError.INVALID, pdf(), fields = mapOf("mediaType" to "*/*"))
        }
    }

    @Test
    fun `Upload refused without a file part`() {
        validationRun {
            val ex = assertThrows<EvidenceException> {
                asCreator { evidenceController.upload(id(), MockMultipartHttpServletRequest()) }
            }
            assertEquals(EvidenceError.INVALID, ex.error)
        }
    }

    @Test
    fun `Upload refused while the licence is off`() {
        validationRun {
            withoutTrail {
                refused(EvidenceError.NOT_LICENSED, pdf())
            }
        }
    }

    // Authorizations

    @Test
    fun `Upload refused to a user who can only see the validation run`() {
        validationRun {
            assertThrows<AccessDeniedException> {
                asUserWithView(this).call { evidenceController.upload(id(), request(pdf())) }
            }
        }
    }

    @Test
    fun `Upload refused before its content is read`() {
        validationRun {
            assertThrows<AccessDeniedException> {
                asUserWithView(this).call {
                    evidenceService.attach(
                        this,
                        EvidenceUpload(fileName = "report.pdf", mediaType = "application/pdf", size = 4, content = { fail("Content read") }),
                    )
                }
            }
        }
    }

    @Test
    fun `Upload, listing and download refused to a user who cannot see the validation run`() {
        validationRun {
            val evidence = upload(pdf())
            withNoGrantViewToAll {
                asUser().withProjectFunction(this, ValidationRunCreate::class.java).call {
                    assertThrows<AccessDeniedException> { evidenceController.upload(id(), request(pdf())) }
                }
                asUser().call {
                    assertThrows<AccessDeniedException> { evidenceService.getEvidences(this) }
                    assertThrows<AccessDeniedException> { download(evidence.id) }
                }
            }
        }
    }

    // Download

    @Test
    fun `PDF downloaded inline with its bytes, without sniffing`() {
        validationRun {
            val content = pdf()
            val evidence = upload(content, fileName = "trivy report.pdf")
            val download = download(evidence.id)
            assertTrue(content.contentEquals(download.content))
            assertEquals("application/pdf", download.headers.getFirst(HttpHeaders.CONTENT_TYPE))
            assertEquals(content.size.toString(), download.headers.getFirst(HttpHeaders.CONTENT_LENGTH))
            assertEquals("inline; filename=\"trivy report.pdf\"; filename*=UTF-8''trivy%20report.pdf", download.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION))
            assertEquals("nosniff", download.headers.getFirst("X-Content-Type-Options"))
            assertEquals("default-src 'none'; style-src 'unsafe-inline'", download.headers.getFirst("Content-Security-Policy"))
        }
    }

    @Test
    fun `Image downloaded inline in a sandbox`() {
        validationRun {
            val evidence = upload(png(), fileName = "screenshot.png", partType = "image/png")
            val download = download(evidence.id)
            assertEquals("image/png", download.headers.getFirst(HttpHeaders.CONTENT_TYPE))
            assertTrue(download.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)!!.startsWith("inline;"))
            assertEquals("default-src 'none'; style-src 'unsafe-inline'; sandbox", download.headers.getFirst("Content-Security-Policy"))
        }
    }

    @Test
    fun `HTML downloaded as an octet-stream attachment in a sandbox`() {
        validationRun {
            val content = html()
            val evidence = upload(content, fileName = "zap.html", partType = "text/html")
            assertEquals("text/html", evidence.mediaType)
            val download = download(evidence.id)
            assertTrue(content.contentEquals(download.content))
            assertEquals("application/octet-stream", download.headers.getFirst(HttpHeaders.CONTENT_TYPE))
            assertEquals("attachment; filename=\"zap.html\"; filename*=UTF-8''zap.html", download.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION))
            assertEquals("nosniff", download.headers.getFirst("X-Content-Type-Options"))
            assertEquals("default-src 'none'; style-src 'unsafe-inline'; sandbox", download.headers.getFirst("Content-Security-Policy"))
        }
    }

    @Test
    fun `SVG downloaded as an octet-stream attachment`() {
        validationRun {
            val content = """<svg xmlns="http://www.w3.org/2000/svg" onload="alert('${uid("s-")}')"/>""".toByteArray()
            val evidence = upload(content, fileName = "chart.svg", partType = "image/svg+xml")
            val download = download(evidence.id)
            assertEquals("application/octet-stream", download.headers.getFirst(HttpHeaders.CONTENT_TYPE))
            assertTrue(download.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)!!.startsWith("attachment;"))
        }
    }

    @Test
    fun `HTML spoofed as a PDF downloaded as an octet-stream attachment`() {
        validationRun {
            val evidence = upload(html(), fileName = "report.pdf", partType = "application/pdf")
            val download = download(evidence.id)
            assertEquals("application/octet-stream", download.headers.getFirst(HttpHeaders.CONTENT_TYPE))
            assertTrue(download.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)!!.startsWith("attachment;"))
            assertEquals("default-src 'none'; style-src 'unsafe-inline'; sandbox", download.headers.getFirst("Content-Security-Policy"))
        }
    }

    @Test
    fun `HTML spoofed as plain text downloaded as plain text, without sniffing`() {
        validationRun {
            val content = html()
            val evidence = upload(content, fileName = "notes.txt", partType = "text/plain")
            val download = download(evidence.id)
            // Text, never HTML: with nosniff, a browser renders it as text
            assertEquals("text/plain;charset=UTF-8", download.headers.getFirst(HttpHeaders.CONTENT_TYPE))
            assertEquals("nosniff", download.headers.getFirst("X-Content-Type-Options"))
            assertEquals("default-src 'none'; style-src 'unsafe-inline'; sandbox", download.headers.getFirst("Content-Security-Policy"))
        }
    }

    @Test
    fun `Download of a file name with quotes and non-ASCII characters`() {
        validationRun {
            val evidence = upload(pdf(), fileName = "rapport \"été\".pdf")
            val disposition = download(evidence.id).headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)!!
            // An ASCII fallback, its quotes escaped, and the UTF-8 name, encoded
            assertEquals("inline; filename=\"rapport \\\"ete\\\".pdf\"; filename*=UTF-8''rapport%20%22%C3%A9t%C3%A9%22.pdf", disposition)
        }
    }

    @Test
    fun `Download by a user who can only see the validation run`() {
        validationRun {
            val content = pdf()
            val evidence = upload(content)
            assertTrue(content.contentEquals(downloadAsViewer(evidence.id).content))
        }
    }

    @Test
    fun `Listing and download after the licence lapses`() {
        validationRun {
            val content = pdf()
            val evidence = upload(content)
            withoutTrail {
                assertEquals(listOf(evidence.id), asUserWithView(this).call { evidenceService.getEvidences(this) }.map { it.id })
                assertTrue(content.contentEquals(downloadAsViewer(evidence.id).content))
            }
        }
    }

    @Test
    fun `Download of an evidence whose content is missing from the storage`() {
        validationRun {
            val content = pdf()
            val evidence = upload(content)
            deleteBlob(evidence.sha256)
            assertThrows<EvidenceNotFoundException> { download(evidence.id) }
        }
    }

    @Test
    fun `Download of an evidence which does not exist`() {
        assertThrows<EvidenceNotFoundException> { asAdmin { download(Int.MAX_VALUE) } }
    }

    // Storage

    /**
     * Runs [code] with an evidence controller whose storage is the one of the stack, changed by
     * [storage].
     */
    private fun <T> withStorage(
        storage: AuditTrailConfigProperties.StorageProperties.() -> Unit,
        code: (EvidenceController) -> T,
    ): T {
        val stack = auditTrailConfigProperties.storage
        val properties = AuditTrailConfigProperties().apply {
            this.storage.endpoint = stack.endpoint
            this.storage.bucket = stack.bucket
            this.storage.region = stack.region
            this.storage.pathStyle = stack.pathStyle
            this.storage.accessKey = stack.accessKey
            this.storage.secretKey = stack.secretKey
            this.storage.storage()
        }
        val storageService = EvidenceStorageServiceImpl(properties)
        val service = EvidenceServiceImpl(
            structureService = structureService,
            securityService = securityService,
            auditTrailLicense = auditTrailLicense,
            evidenceStorageService = storageService,
            evidenceBlobStore = EvidenceBlobStoreImpl(storageService, properties),
            evidenceRepository = evidenceRepository,
            trailService = trailService,
            eventPostService = eventPostService,
            transactionTemplate = transactionTemplate,
        )
        return try {
            code(EvidenceController(structureService, service))
        } finally {
            storageService.destroy()
        }
    }

    @Test
    fun `Upload refused while the storage is not configured`() {
        validationRun {
            withStorage({ endpoint = null }) { controller ->
                val ex = assertThrows<EvidenceException> {
                    asCreator { controller.upload(id(), request(pdf())) }
                }
                assertEquals(EvidenceError.STORAGE_NOT_CONFIGURED, ex.error)
                assertEquals(503, controller.onEvidenceException(ex).statusCode.value())
                assertEquals("audit-trail.evidence.storage-not-configured", controller.onEvidenceException(ex).body?.code)
            }
        }
    }

    @Test
    fun `Upload refused while the storage is unreachable`() {
        validationRun {
            withStorage({ bucket = "yontrack-no-such-bucket" }) { controller ->
                val ex = assertThrows<EvidenceException> {
                    asCreator { controller.upload(id(), request(pdf())) }
                }
                assertEquals(EvidenceError.STORAGE_UNREACHABLE, ex.error)
                assertEquals("audit-trail.evidence.storage-unreachable", controller.onEvidenceException(ex).body?.code)
            }
        }
    }

    @Test
    fun `Download refused while the storage is not configured`() {
        validationRun {
            val evidence = upload(pdf())
            withStorage({ endpoint = null }) { controller ->
                val ex = assertThrows<EvidenceException> {
                    asUserWithView(this).call { download(evidence.id, controller) }
                }
                assertEquals(EvidenceError.STORAGE_NOT_CONFIGURED, ex.error)
            }
        }
    }

    // Verification

    private fun deleteBlob(sha256: String) {
        client.s3.deleteObject(DeleteObjectRequest.builder().bucket(client.bucket).key("blobs/$sha256").build())
    }

    private fun alterBlob(sha256: String) {
        client.s3.putObject(
            PutObjectRequest.builder().bucket(client.bucket).key("blobs/$sha256").build(),
            RequestBody.fromBytes("altered ${uid("a-")}".toByteArray()),
        )
    }

    @Test
    fun `Verification with the evidence finds them all intact`() {
        validationRun {
            upload(pdf())
            upload(png(), partType = "image/png")
            val verification = asUserWithView(this).call { trailVerificationService.verify(build, includeEvidence = true) }
            assertTrue(verification.chainIntact)
            assertEquals(emptyList(), verification.missingEvidence)
            assertEquals(emptyList(), verification.alteredEvidence)
        }
    }

    @Test
    fun `Verification with the evidence finds a missing and an altered blob`() {
        validationRun {
            // Trail: build.created (1), validation.run (2), then evidence.attached (3, 4, 5)
            upload(pdf())
            val missing = upload(pdf())
            val altered = upload(pdf())
            deleteBlob(missing.sha256)
            alterBlob(altered.sha256)
            val verification = asUserWithView(this).call { trailVerificationService.verify(build, includeEvidence = true) }
            assertTrue(verification.chainIntact, "The trail itself is intact")
            assertEquals(listOf(4), verification.missingEvidence)
            assertEquals(listOf(5), verification.alteredEvidence)
        }
    }

    @Test
    fun `Verification without the evidence does not read the storage`() {
        validationRun {
            val evidence = upload(pdf())
            deleteBlob(evidence.sha256)
            val verification = asUserWithView(this).call { trailVerificationService.verify(build) }
            assertNull(verification.missingEvidence)
            assertNull(verification.alteredEvidence)
        }
    }

    @Test
    fun `Evidence rows go with their validation run`() {
        validationRun {
            val evidence = upload(pdf())
            asAdmin { structureService.deleteValidationRun(this) }
            assertNull(evidenceRepository.findById(evidence.id))
            assertFalse(blob(evidence.sha256) == null, "The blob is left for the collection of the blobs")
        }
    }
}
