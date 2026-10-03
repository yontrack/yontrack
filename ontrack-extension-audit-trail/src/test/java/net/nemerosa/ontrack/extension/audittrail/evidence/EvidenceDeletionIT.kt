package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.extension.audittrail.AuditTrailConfigProperties
import net.nemerosa.ontrack.extension.audittrail.events.AuditTrailEvents
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.security.EvidenceAuthorizationContributor
import net.nemerosa.ontrack.extension.audittrail.security.EvidenceDelete
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageServiceImpl
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerificationService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.security.Roles
import net.nemerosa.ontrack.model.security.ValidationRunCreate
import net.nemerosa.ontrack.model.structure.ValidationRun
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deletion of an evidence: its row is kept, marked as deleted, `evidence.deleted` is written to the
 * trail of its build, and its blob is removed unless another evidence still references it.
 */
class EvidenceDeletionIT : AbstractEvidenceITSupport() {

    @Autowired
    private lateinit var trailService: TrailService

    @Autowired
    private lateinit var trailVerificationService: TrailVerificationService

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    @Autowired
    private lateinit var eventPostService: EventPostService

    @Autowired
    private lateinit var auditTrailLicense: AuditTrailLicense

    @Autowired
    private lateinit var auditTrailConfigProperties: AuditTrailConfigProperties

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var evidenceBlobCollector: EvidenceBlobCollector

    /**
     * Runs [code] as an owner of the project of the run.
     */
    private fun <T> ValidationRun.asOwner(code: () -> T): T {
        val account = asAdmin { doCreateAccountWithProjectRole(project, Roles.PROJECT_OWNER) }
        return asFixedAccount(account, code)
    }

    private fun ValidationRun.delete(id: Int): Evidence = asOwner { evidenceService.delete(id) }

    // Deletion

    @Test
    fun `Deletion keeps the evidence, marked as deleted`() {
        validationRun {
            val evidence = upload(pdf())
            val deleted = delete(evidence.id)
            assertEquals(evidence.id, deleted.id)
            assertNotNull(deleted.deletedAt, "Marked as deleted")
            val listed = asUserWithView(this).call { evidenceService.getEvidences(this) }.single()
            assertEquals(deleted.deletedAt, listed.deletedAt)
            assertThrows<EvidenceNotFoundException> { asAdmin { evidenceService.getEvidence(evidence.id) } }
            assertThrows<EvidenceNotFoundException> { asAdmin { evidenceService.download(evidence.id) } }
        }
    }

    @Test
    fun `Deletion appends evidence deleted to the trail of the build`() {
        validationRun {
            val content = pdf()
            val evidence = upload(content)
            val deleted = delete(evidence.id)
            val entry = asAdmin { trailService.getEntries(build) }.last()
            // build.created (1), validation.run (2), evidence.attached (3), evidence.deleted (4)
            assertEquals(4, entry.seq)
            assertEquals(TrailEntryTypes.EVIDENCE_DELETED, entry.type)
            assertEquals(
                mapOf(
                    "validationStamp" to mapOf("id" to validationStamp.id(), "name" to validationStamp.name),
                    "validationRun" to mapOf("id" to id(), "order" to 1),
                    "evidence" to mapOf(
                        "id" to evidence.id,
                        "fileName" to "report.pdf",
                        "sha256" to sha256(content),
                    ),
                ).asJson(),
                entry.payload,
            )
            assertEquals("ui", entry.actor.path("via").asString())
            assertNotNull(deleted.deletedAt)
        }
    }

    @Test
    fun `Deletion posts evidence deleted`() {
        validationRun {
            val content = pdf()
            val evidence = upload(content)
            delete(evidence.id)
            val event = assertNotNull(asAdmin { eventQueryService.getLastEvent(build, AuditTrailEvents.EVIDENCE_DELETED) })
            assertEquals(evidence.id.toString(), event.getValue(AuditTrailEvents.EVENT_EVIDENCE_ID))
            assertEquals("report.pdf", event.getValue(AuditTrailEvents.EVENT_EVIDENCE_FILE_NAME))
            assertEquals(sha256(content), event.getValue(AuditTrailEvents.EVENT_EVIDENCE_SHA256))
        }
    }

    @Test
    fun `Deletion removes a blob no other evidence references`() {
        validationRun {
            val content = pdf()
            val evidence = upload(content)
            delete(evidence.id)
            assertNull(blob(sha256(content)), "Blob removed")
        }
    }

    @Test
    fun `Deletion keeps a blob another evidence references, until it is deleted too`() {
        validationRun {
            val content = pdf()
            val first = upload(content)
            val second = upload(content, fileName = "again.pdf")
            delete(first.id)
            assertTrue(content.contentEquals(blob(sha256(content))), "Blob kept for the second evidence")
            assertTrue(content.contentEquals(asUserWithView(this).call {
                evidenceService.download(second.id).stream.use { it.readAllBytes() }
            }))
            delete(second.id)
            assertNull(blob(sha256(content)), "Blob removed with the last evidence")
        }
    }

    @Test
    fun `Deletion keeps a blob an evidence of another build references`() {
        val content = pdf()
        var shared: Int? = null
        validationRun {
            shared = upload(content).id
        }
        validationRun {
            val evidence = upload(content)
            delete(evidence.id)
        }
        assertTrue(content.contentEquals(blob(sha256(content))), "Blob kept for the evidence of the other build")
        assertNotNull(shared)
    }

    @Test
    fun `Deletion of an evidence already deleted`() {
        validationRun {
            val evidence = upload(pdf())
            delete(evidence.id)
            assertThrows<EvidenceNotFoundException> { delete(evidence.id) }
            // Only one entry
            assertEquals(
                1,
                asAdmin { trailService.getEntries(build) }.count { it.type == TrailEntryTypes.EVIDENCE_DELETED }
            )
        }
    }

    @Test
    fun `Deletion of an evidence which does not exist`() {
        assertThrows<EvidenceNotFoundException> { asAdmin { evidenceService.delete(Int.MAX_VALUE) } }
    }

    @Test
    fun `Deletion refused while the licence is off`() {
        validationRun {
            val evidence = upload(pdf())
            val ex = assertThrows<EvidenceException> {
                withoutTrail { delete(evidence.id) }
            }
            assertEquals(EvidenceError.NOT_LICENSED, ex.error)
            assertNull(asAdmin { evidenceService.getEvidence(evidence.id) }.deletedAt)
            assertTrue(evidence.sha256.let { blob(it) } != null)
        }
    }

    @Test
    fun `Deletion while the storage is not configured leaves the blob to the sweep`() {
        validationRun {
            val content = pdf()
            val evidence = upload(content)
            val storageService = EvidenceStorageServiceImpl(AuditTrailConfigProperties())
            try {
                val blobStore = EvidenceBlobStoreImpl(storageService, auditTrailConfigProperties)
                val service = EvidenceServiceImpl(
                    structureService = structureService,
                    securityService = securityService,
                    auditTrailLicense = auditTrailLicense,
                    evidenceStorageService = storageService,
                    evidenceBlobStore = blobStore,
                    evidenceRepository = evidenceRepository,
                    evidenceBlobCollector = EvidenceBlobCollectorImpl(blobStore, evidenceRepository, transactionTemplate),
                    trailService = trailService,
                    eventPostService = eventPostService,
                    transactionTemplate = transactionTemplate,
                )
                val deleted = asOwner { service.delete(evidence.id) }
                assertNotNull(deleted.deletedAt)
            } finally {
                storageService.destroy()
            }
            assertTrue(content.contentEquals(blob(sha256(content))), "Blob left to the sweep")
            // ... which collects it
            evidenceBlobCollector.collect(sha256(content))
            assertNull(blob(sha256(content)))
        }
    }

    // Authorizations

    @Test
    fun `Deletion by an administrator`() {
        validationRun {
            val evidence = upload(pdf())
            asAdmin { evidenceService.delete(evidence.id) }
            assertNotNull(asAdmin { evidenceRepository.findById(evidence.id) }?.deletedAt)
        }
    }

    @Test
    fun `Deletion refused to a creator of validation runs`() {
        validationRun {
            val evidence = upload(pdf())
            assertThrows<AccessDeniedException> {
                asCreator { evidenceService.delete(evidence.id) }
            }
            assertNull(evidenceRepository.findById(evidence.id)?.deletedAt)
            assertTrue(blob(evidence.sha256) != null)
        }
    }

    @Test
    fun `Deletion refused to the CI global roles`() {
        validationRun {
            val evidence = upload(pdf())
            listOf(Roles.GLOBAL_AUTOMATION, Roles.GLOBAL_CONTROLLER).forEach { role ->
                assertThrows<AccessDeniedException>("Global role $role") {
                    asGlobalRole(role) { evidenceService.delete(evidence.id) }
                }
            }
            assertNull(evidenceRepository.findById(evidence.id)?.deletedAt)
        }
    }

    @Test
    fun `Deletion refused to the project roles other than the owner`() {
        validationRun {
            val evidence = upload(pdf())
            (Roles.PROJECT_ROLES - Roles.PROJECT_OWNER).forEach { role ->
                val account = asAdmin { doCreateAccountWithProjectRole(project, role) }
                assertThrows<AccessDeniedException>("Project role $role") {
                    asFixedAccount(account) { evidenceService.delete(evidence.id) }
                }
            }
            assertNull(evidenceRepository.findById(evidence.id)?.deletedAt)
        }
    }

    @Test
    fun `Deletion refused to a user who cannot see the validation run`() {
        validationRun {
            val evidence = upload(pdf())
            withNoGrantViewToAll {
                assertThrows<AccessDeniedException> {
                    asUser().call { evidenceService.delete(evidence.id) }
                }
            }
            assertNull(evidenceRepository.findById(evidence.id)?.deletedAt)
        }
    }

    @Test
    fun `Only the project owner among the built-in project roles may delete evidence`() {
        Roles.PROJECT_ROLES.forEach { id ->
            val role = rolesService.getProjectRole(id).orElseThrow()
            assertEquals(id == Roles.PROJECT_OWNER, role.isGranted(EvidenceDelete::class.java), "Project role $id")
        }
    }

    @Test
    fun `Only the administrator among the built-in global roles may delete evidence`() {
        Roles.GLOBAL_ROLES.forEach { id ->
            val role = rolesService.getGlobalRole(id).orElseThrow()
            assertEquals(
                id == Roles.GLOBAL_ADMINISTRATOR,
                role.isProjectFunctionGranted(EvidenceDelete::class.java),
                "Global role $id"
            )
        }
    }

    // Verification

    @Test
    fun `Verification with the evidence does not report the blob of a deleted evidence`() {
        validationRun {
            // build.created (1), validation.run (2), evidence.attached (3, 4)
            val deleted = upload(pdf())
            val kept = upload(pdf())
            delete(deleted.id)
            assertNull(blob(deleted.sha256))
            deleteBlob(kept.sha256)
            val verification = asUserWithView(this).call { trailVerificationService.verify(build, includeEvidence = true) }
            assertTrue(verification.chainIntact)
            assertEquals(listOf(4), verification.missingEvidence, "Only the evidence which is not deleted")
            assertEquals(emptyList(), verification.alteredEvidence)
        }
    }

    // REST

    @Test
    fun `Deletion through the REST API`() {
        validationRun {
            val evidence = upload(pdf())
            val view = asOwner { evidenceController.delete(evidence.id) }
            assertEquals(evidence.id, view.id)
            assertNotNull(view.deletedAt)
            assertNull(view.downloadUrl)
        }
    }

    // GraphQL

    @Test
    fun `Deletion through GraphQL`() {
        validationRun {
            val evidence = upload(pdf())
            asOwner {
                run(
                    """
                        mutation {
                            deleteEvidence(input: {id: ${evidence.id}}) {
                                evidence { id deletedAt downloadUrl }
                                errors { message }
                            }
                        }
                    """
                ) { data ->
                    val node = data.path("deleteEvidence")
                    assertTrue(node.path("errors").isNull || node.path("errors").isEmpty)
                    assertEquals(evidence.id, node.path("evidence").path("id").asInt())
                    assertFalse(node.path("evidence").path("deletedAt").isNull)
                    assertTrue(node.path("evidence").path("downloadUrl").isNull)
                }
            }
        }
    }

    @Test
    fun `Authorization to delete the evidence of a validation run`() {
        validationRun {
            val query = """
                {
                    validationRuns(id: ${id()}) {
                        authorizations { name action authorized }
                    }
                }
            """
            fun authorized(): Boolean? {
                var result: Boolean? = null
                run(query) { data ->
                    result = data.path("validationRuns").single().path("authorizations")
                        .firstOrNull {
                            it.path("name").asString() == EvidenceAuthorizationContributor.EVIDENCE &&
                                    it.path("action").asString() == "delete"
                        }
                        ?.path("authorized")?.asBoolean()
                }
                return result
            }
            assertEquals(true, asOwner { authorized() })
            assertEquals(false, asCreator { authorized() })
            assertEquals(false, withoutTrail { asOwner { authorized() } }, "Not while the licence is off")
        }
    }

    @Test
    fun `Deletion refused to a creator of validation runs through the REST API`() {
        validationRun {
            val evidence = upload(pdf())
            assertThrows<AccessDeniedException> {
                asUserWithView(this).withProjectFunction(this, ValidationRunCreate::class.java).call {
                    evidenceController.delete(evidence.id)
                }
            }
        }
    }
}
