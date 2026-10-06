package net.nemerosa.ontrack.extension.audittrail.archive

import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import net.nemerosa.ontrack.extension.audittrail.evidence.AbstractEvidenceITSupport
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceBlobContent
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceBlobStore
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceBlobStoreImpl
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceError
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceException
import net.nemerosa.ontrack.extension.audittrail.export.TrailExport
import net.nemerosa.ontrack.extension.audittrail.export.TrailExportService
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicensedFeatureProvider.Companion.FEATURE_AUDIT_TRAIL
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageServiceImpl
import net.nemerosa.ontrack.extension.audittrail.ui.AuditTrailExportController
import net.nemerosa.ontrack.extension.audittrail.ui.AuditTrailNotFoundException
import net.nemerosa.ontrack.extension.audittrail.ui.EvidenceArchiveController
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerifier
import net.nemerosa.ontrack.json.ObjectMapperFactory
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletResponse
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The evidence archive of a build, on
 * `GET /rest/extension/audit-trail/builds/{buildId}/evidence-archive`, against the MinIO of the
 * integration test stack.
 */
class EvidenceArchiveIT : AbstractEvidenceITSupport() {

    @Autowired
    private lateinit var evidenceArchiveController: EvidenceArchiveController

    @Autowired
    private lateinit var auditTrailExportController: AuditTrailExportController

    @Autowired
    private lateinit var auditTrailConfigProperties: AuditTrailConfigProperties

    @Autowired
    private lateinit var trailService: TrailService

    @Autowired
    private lateinit var trailExportService: TrailExportService

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    private val json = ObjectMapperFactory.create()

    /**
     * A downloaded archive: its response, and its files in the order of the ZIP.
     */
    private inner class Archive(val response: MockHttpServletResponse) {

        val files: List<Pair<String, ByteArray>> =
            ZipInputStream(ByteArrayInputStream(response.contentAsByteArray)).use { zip ->
                generateSequence { zip.nextEntry }.map { it.name to zip.readAllBytes() }.toList()
            }

        val names: List<String> get() = files.map { it.first }

        fun file(name: String): ByteArray = files.single { it.first == name }.second

        val trail: JsonNode by lazy { json.readTree(file("audit-trail.json")) }

        val manifest: JsonNode by lazy { json.readTree(file("manifest.json")) }

        /**
         * Item of the manifest for an evidence.
         */
        fun item(id: Int): JsonNode = manifest.path("evidence").single { it.path("id").asInt() == id }
    }

    /**
     * Downloads the archive of a build, as a user who can only see it.
     */
    private fun Build.archive(controller: EvidenceArchiveController = evidenceArchiveController): Archive =
        asUserWithView(this).call {
            val response = MockHttpServletResponse()
            controller.archive(id(), response)
            assertEquals(200, response.status)
            Archive(response)
        }

    /**
     * Runs [code] with an archive controller whose storage is the one of the stack, changed by
     * [storage], and whose blob store is the one of this storage, changed by [blobStore].
     */
    private fun <T> withStorage(
        storage: AuditTrailConfigProperties.StorageProperties.() -> Unit = {},
        blobStore: (EvidenceBlobStore) -> EvidenceBlobStore = { it },
        code: (EvidenceArchiveController) -> T,
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
        val storageService: EvidenceStorageService = EvidenceStorageServiceImpl(properties)
        val service = EvidenceArchiveServiceImpl(
            structureService = structureService,
            evidenceService = evidenceService,
            trailExportService = trailExportService,
            evidenceStorageService = storageService,
            evidenceBlobStore = blobStore(EvidenceBlobStoreImpl(storageService, properties)),
            jsonMapper = jsonMapper,
        )
        return try {
            code(EvidenceArchiveController(structureService, trailService, service))
        } finally {
            (storageService as EvidenceStorageServiceImpl).destroy()
        }
    }

    @Test
    fun `The archive is a ZIP file to download, named after the build`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        validate(vs).upload(pdf())
                        val archive = archive()
                        assertEquals("application/zip", archive.response.contentType)
                        val disposition = archive.response.getHeader(HttpHeaders.CONTENT_DISPOSITION)!!
                        assertTrue(disposition.startsWith("attachment"), "Download: $disposition")
                        assertTrue(
                            disposition.contains("audit-trail-${project.name}-${branch.name}-${name}.zip"),
                            "File name: $disposition"
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Every active evidence under its stamp and its run, between the trail and the manifest`() {
        asAdmin {
            project {
                branch {
                    val scan = validationStamp("scan")
                    val tests = validationStamp("tests")
                    build {
                        val firstScan = validate(scan)
                        val testRun = validate(tests)
                        val secondScan = validate(scan)
                        val a = pdf()
                        val b = png()
                        val c = pdf()
                        // Interleaved, so that the order of the upload is not the one of the runs
                        val evidenceA = secondScan.upload(a, fileName = "a.pdf")
                        val evidenceB = firstScan.upload(b, fileName = "b.png", partType = "image/png")
                        val evidenceC = testRun.upload(c, fileName = "c.pdf")

                        val archive = archive()
                        assertEquals(
                            listOf(
                                "audit-trail.json",
                                "scan/${secondScan.runOrder}/${evidenceA.id}-a.pdf",
                                "scan/${firstScan.runOrder}/${evidenceB.id}-b.png",
                                "tests/${testRun.runOrder}/${evidenceC.id}-c.pdf",
                                "manifest.json",
                            ),
                            archive.names,
                        )
                        assertTrue(a.contentEquals(archive.file("scan/${secondScan.runOrder}/${evidenceA.id}-a.pdf")))
                        assertTrue(b.contentEquals(archive.file("scan/${firstScan.runOrder}/${evidenceB.id}-b.png")))
                        assertTrue(c.contentEquals(archive.file("tests/${testRun.runOrder}/${evidenceC.id}-c.pdf")))
                    }
                }
            }
        }
    }

    @Test
    fun `The trail of the archive is its export, which vouches for the files of the archive`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        val content = pdf()
                        val evidence = validate(vs).upload(content)

                        val archive = archive()
                        val export = asUserWithView(this).call { auditTrailExportController.export(id()) }.body!!.asJson()
                        listOf("exportVersion", "build", "keys", "entries").forEach { field ->
                            assertEquals(export.path(field), archive.trail.path(field), "Same $field as the export")
                        }
                        // Verified offline as the export
                        val trail = json.treeToValue(archive.trail, TrailExport::class.java)
                        assertEquals(true, TrailVerifier.verify(trail).chainIntact)
                        assertEquals(true, TrailVerifier.verify(trail).endorsementsValid)
                        // The SHA-256 of the file is the one the trail records
                        val attached = trail.entries.single { it.type == TrailEntryTypes.EVIDENCE_ATTACHED }
                        assertEquals(evidence.id, attached.payload.path("evidence").path("id").asInt())
                        val path = archive.item(evidence.id).path("path").asString()
                        assertEquals(sha256(archive.file(path)), attached.payload.path("evidence").path("sha256").asString())
                    }
                }
            }
        }
    }

    @Test
    fun `The manifest describes every evidence of the build`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("scan")
                    build {
                        val run = validate(vs)
                        val content = pdf()
                        val uploaded = run.upload(
                            content,
                            fileName = "trivy.pdf",
                            fields = mapOf(
                                "sourceTool" to "trivy",
                                "sourceVersion" to "0.50.1",
                                "sourceUrl" to "https://ci.example.com/job/1",
                                "externalDigest" to "sha256:${sha256(content)}",
                            ),
                        )
                        val evidence = evidenceService.getEvidence(uploaded.id)

                        val archive = archive()
                        val manifest = archive.manifest
                        assertEquals(1, manifest.path("manifestVersion").asInt())
                        assertEquals(archive.trail.path("exportedAt"), manifest.path("exportedAt"))
                        assertEquals(id(), manifest.path("build").path("id").asInt())
                        assertEquals(project.name, manifest.path("build").path("project").asString())
                        assertEquals(branch.name, manifest.path("build").path("branch").asString())
                        assertEquals(name, manifest.path("build").path("name").asString())

                        val item = archive.item(evidence.id)
                        assertEquals("trivy.pdf", item.path("fileName").asString())
                        assertEquals("application/pdf", item.path("mediaType").asString())
                        assertEquals(content.size.toLong(), item.path("size").asLong())
                        assertEquals(sha256(content), item.path("sha256").asString())
                        assertEquals(TrailHashFormatV1.formatTime(evidence.collectedAt), item.path("collectedAt").asString())
                        assertEquals(evidence.collectedBy, item.path("collectedBy"))
                        assertEquals("trivy", item.path("source").path("tool").asString())
                        assertEquals("0.50.1", item.path("source").path("version").asString())
                        assertEquals("https://ci.example.com/job/1", item.path("source").path("url").asString())
                        assertEquals(evidence.externalDigest, item.path("externalDigest").asString())
                        assertEquals(run.id(), item.path("validationRun").path("id").asInt())
                        assertEquals("scan", item.path("validationRun").path("validationStamp").asString())
                        assertEquals(run.runOrder, item.path("validationRun").path("runOrder").asInt())
                        assertEquals("active", item.path("state").asString())
                        assertEquals("scan/${run.runOrder}/${evidence.id}-trivy.pdf", item.path("path").asString())
                        assertFalse(item.has("actualSha256"), "No actual SHA-256 for an intact evidence")
                        assertFalse(item.has("deletedAt"), "No deletion for an active evidence")
                    }
                }
            }
        }
    }

    @Test
    fun `Deleted evidence is in the manifest, without a file`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        val run = validate(vs)
                        val kept = run.upload(pdf(), fileName = "kept.pdf")
                        val deleted = run.upload(pdf(), fileName = "deleted.pdf")
                        evidenceService.delete(deleted.id)
                        val deletedAt = evidenceRepository.findById(deleted.id)!!.deletedAt!!

                        val archive = archive()
                        assertEquals(
                            listOf("audit-trail.json", "${vs.name}/1/${kept.id}-kept.pdf", "manifest.json"),
                            archive.names,
                        )
                        val item = archive.item(deleted.id)
                        assertEquals("deleted", item.path("state").asString())
                        assertEquals(TrailHashFormatV1.formatTime(deletedAt), item.path("deletedAt").asString())
                        assertFalse(item.has("path"), "No file for a deleted evidence")
                        assertEquals("active", archive.item(kept.id).path("state").asString())
                    }
                }
            }
        }
    }

    @Test
    fun `Evidence gone with its validation run is not in the archive, its trail saying what became of it`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        val gone = validate(vs)
                        val goneEvidence = gone.upload(pdf(), fileName = "gone.pdf")
                        val keptRun = validate(vs)
                        val kept = keptRun.upload(pdf(), fileName = "kept.pdf")
                        structureService.deleteValidationRun(gone)
                        // The order of a run is its rank among the runs left
                        val keptOrder = structureService.getValidationRun(keptRun.id).runOrder

                        val archive = archive()
                        assertEquals(
                            listOf("audit-trail.json", "${vs.name}/$keptOrder/${kept.id}-kept.pdf", "manifest.json"),
                            archive.names,
                        )
                        assertEquals(
                            listOf(kept.id),
                            archive.manifest.path("evidence").toList().map { it.path("id").asInt() },
                        )
                        val deleted = archive.trail.path("entries").single {
                            it.path("type").asString() == TrailEntryTypes.EVIDENCE_DELETED
                        }
                        assertEquals(goneEvidence.id, deleted.path("payload").path("evidence").path("id").asInt())
                    }
                }
            }
        }
    }

    @Test
    fun `Evidence missing from the storage is in the manifest, without a file, and the archive is still downloaded`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        val run = validate(vs)
                        val content = pdf()
                        val missing = run.upload(content, fileName = "missing.pdf")
                        val present = run.upload(pdf(), fileName = "present.pdf")
                        deleteBlob(sha256(content))

                        val archive = archive()
                        assertEquals(
                            listOf("audit-trail.json", "${vs.name}/1/${present.id}-present.pdf", "manifest.json"),
                            archive.names,
                        )
                        val item = archive.item(missing.id)
                        assertEquals("missing", item.path("state").asString())
                        assertFalse(item.has("path"), "No file for a missing evidence")
                        assertFalse(item.has("actualSha256"), "No actual SHA-256 for a missing evidence")
                        assertEquals("active", archive.item(present.id).path("state").asString())
                    }
                }
            }
        }
    }

    @Test
    fun `Altered evidence is in the archive as stored, flagged with its actual SHA-256`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        val content = pdf()
                        val evidence = validate(vs).upload(content, fileName = "report.pdf")
                        val altered = "%PDF-1.7\n% altered ${uid("alt-")}\n".toByteArray()
                        put("blobs/${sha256(content)}", altered)

                        val archive = archive()
                        val path = "${vs.name}/1/${evidence.id}-report.pdf"
                        assertTrue(altered.contentEquals(archive.file(path)), "The file as stored")
                        val item = archive.item(evidence.id)
                        assertEquals("altered", item.path("state").asString())
                        assertEquals(sha256(content), item.path("sha256").asString())
                        assertEquals(sha256(altered), item.path("actualSha256").asString())
                        assertEquals(path, item.path("path").asString())
                    }
                }
            }
        }
    }

    @Test
    fun `Two evidences of the same content are two files`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        val content = pdf()
                        val first = validate(vs).upload(content, fileName = "report.pdf")
                        val second = validate(vs).upload(content, fileName = "report.pdf")

                        val archive = archive()
                        val firstPath = "${vs.name}/1/${first.id}-report.pdf"
                        val secondPath = "${vs.name}/2/${second.id}-report.pdf"
                        assertEquals(listOf("audit-trail.json", firstPath, secondPath, "manifest.json"), archive.names)
                        assertTrue(content.contentEquals(archive.file(firstPath)))
                        assertTrue(content.contentEquals(archive.file(secondPath)))
                    }
                }
            }
        }
    }

    @Test
    fun `No file of the archive escapes its folder, whatever the name of its evidence`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        val evidence = validate(vs).upload(pdf(), fileName = "report.pdf")
                        // Behind the back of the upload, which never stores a path
                        namedParameterJdbcTemplate.update(
                            "UPDATE EVIDENCE SET FILE_NAME = :fileName WHERE ID = :id",
                            mapOf("id" to evidence.id, "fileName" to "../../evil.sh"),
                        )

                        val archive = archive()
                        val path = EvidenceArchiveNames.path(vs.name, 1, evidence.id, "../../evil.sh")
                        assertEquals("${vs.name}/1/${evidence.id}-___.._evil.sh", path)
                        assertTrue(path in archive.names, "Sanitised path in ${archive.names}")
                        val target = Path.of("/unzipped")
                        archive.names.forEach { name ->
                            assertTrue(target.resolve(name).normalize().startsWith(target), "$name stays in its folder")
                        }
                        // The manifest keeps the name as stored
                        assertEquals("../../evil.sh", archive.item(evidence.id).path("fileName").asString())
                        assertEquals(path, archive.item(evidence.id).path("path").asString())
                    }
                }
            }
        }
    }

    @Test
    fun `A build with a trail but no evidence has an archive of its trail and its manifest`() {
        asAdmin {
            project {
                branch {
                    build {
                        val archive = archive()
                        assertEquals(listOf("audit-trail.json", "manifest.json"), archive.names)
                        assertEquals(0, archive.manifest.path("evidence").size())
                    }
                }
            }
        }
    }

    @Test
    fun `The archive stays downloadable after the licence lapses`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        val content = pdf()
                        val evidence = validate(vs).upload(content, fileName = "report.pdf")
                        testLicenseService.withoutFeature(FEATURE_AUDIT_TRAIL) {
                            val archive = archive()
                            assertTrue(content.contentEquals(archive.file("${vs.name}/1/${evidence.id}-report.pdf")))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `No archive for a build with no trail`() {
        asAdmin {
            project {
                branch {
                    untrailedBuild {
                        withoutTrail {
                            assertThrows<AuditTrailNotFoundException> { archive() }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `No archive for a user who cannot see the build`() {
        asAdmin {
            project {
                branch {
                    build {
                        withNoGrantViewToAll {
                            asUser().call {
                                assertFails { evidenceArchiveController.archive(id(), MockHttpServletResponse()) }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Archive refused, before anything is written, while the storage is not configured`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        validate(vs).upload(pdf())
                        withStorage({ endpoint = null }) { controller ->
                            val response = MockHttpServletResponse()
                            val ex = assertThrows<EvidenceException> {
                                asUserWithView(this).call { controller.archive(id(), response) }
                            }
                            assertEquals(EvidenceError.STORAGE_NOT_CONFIGURED, ex.error)
                            assertFalse(response.isCommitted, "Nothing written")
                            assertEquals(0, response.contentAsByteArray.size, "Nothing written")
                            val refusal = controller.onEvidenceException(ex)
                            assertEquals(503, refusal.statusCode.value())
                            assertEquals("audit-trail.evidence.storage-not-configured", refusal.body?.code)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Archive refused, before anything is written, while the storage is unreachable`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        validate(vs).upload(pdf())
                        withStorage({ bucket = "yontrack-no-such-bucket" }) { controller ->
                            val response = MockHttpServletResponse()
                            val ex = assertThrows<EvidenceException> {
                                asUserWithView(this).call { controller.archive(id(), response) }
                            }
                            assertEquals(EvidenceError.STORAGE_UNREACHABLE, ex.error)
                            assertEquals(0, response.contentAsByteArray.size, "Nothing written")
                            val refusal = controller.onEvidenceException(ex)
                            assertEquals(503, refusal.statusCode.value())
                            assertEquals("audit-trail.evidence.storage-unreachable", refusal.body?.code)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Archive aborted when the storage fails while it is written`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    build {
                        validate(vs).upload(pdf())
                        val failing: (EvidenceBlobStore) -> EvidenceBlobStore = { store ->
                            object : EvidenceBlobStore by store {
                                override fun open(sha256: String) = EvidenceBlobContent(
                                    stream = object : InputStream() {
                                        override fun read(): Int = throw IOException("Connection reset")
                                    },
                                    size = 100,
                                )
                            }
                        }
                        withStorage(blobStore = failing) { controller ->
                            val response = MockHttpServletResponse()
                            assertThrows<EvidenceArchiveAbortedException> {
                                asUserWithView(this).call { controller.archive(id(), response) }
                            }
                            // What was written is no complete archive: no manifest
                            val names = try {
                                Archive(response).names
                            } catch (_: IOException) {
                                emptyList()
                            }
                            assertFalse("manifest.json" in names, "Truncated archive: $names")
                            // Nothing was sent yet: the error goes without the headers of the archive
                            assertFalse(response.isCommitted)
                            assertEquals(null, response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                        }
                    }
                }
            }
        }
    }
}
