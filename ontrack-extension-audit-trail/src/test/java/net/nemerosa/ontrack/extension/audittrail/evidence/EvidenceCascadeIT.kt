package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.extension.audittrail.events.AuditTrailEvents
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntry
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.extension.audittrail.ui.EvidenceView
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerificationService
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.EventQueryService
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ValidationRun
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Evidence going with its validation run — deleted on its own, or with its validation stamp: the
 * trail of the build records it as deleted, so that the verification of the evidence does not
 * report it as missing once its blob is swept.
 */
class EvidenceCascadeIT : AbstractEvidenceITSupport() {

    @Autowired
    private lateinit var trailService: TrailService

    @Autowired
    private lateinit var trailVerificationService: TrailVerificationService

    @Autowired
    private lateinit var evidenceBlobCollector: EvidenceBlobCollector

    @Autowired
    private lateinit var eventQueryService: EventQueryService

    private fun Build.trail(): List<TrailEntry> = asAdmin { trailService.getEntries(this) }

    private fun Build.trailAfter(seq: Int): List<TrailEntry> = trail().filter { it.seq > seq }

    private fun Build.lastSeq(): Int = trail().last().seq

    private fun ValidationRun.ref(): Map<String, Any> = mapOf(
        "validationStamp" to mapOf("id" to validationStamp.id(), "name" to validationStamp.name),
        "validationRun" to mapOf("id" to id(), "order" to runOrder),
    )

    private fun evidenceDeleted(run: ValidationRun, evidence: EvidenceView, reason: String) =
        (run.ref() + mapOf(
            "evidence" to mapOf("id" to evidence.id, "fileName" to evidence.fileName, "sha256" to evidence.sha256),
            "reason" to reason,
        )).asJson()

    private fun List<TrailEntry>.assertSameActor() {
        assertEquals(1, map { it.actor }.distinct().size, "Entries written by the actor of the deletion")
    }

    @Test
    fun `Deleting a validation run writes evidence deleted for each of its evidences, before validation deleted`() {
        validationRun {
            val first = upload(pdf())
            val deletedByUser = upload(pdf(), fileName = "mistake.pdf")
            val second = upload(pdf(), fileName = "second.pdf")
            asAdmin { evidenceService.delete(deletedByUser.id) }
            val seq = build.lastSeq()

            asAdmin { structureService.deleteValidationRun(this) }

            val entries = build.trailAfter(seq)
            assertEquals(
                listOf(TrailEntryTypes.EVIDENCE_DELETED, TrailEntryTypes.EVIDENCE_DELETED, TrailEntryTypes.VALIDATION_DELETED),
                entries.map { it.type },
                "No second entry for the evidence already deleted",
            )
            val reason = "cascade/validation-run-deleted"
            assertEquals(evidenceDeleted(this, first, reason), entries[0].payload)
            assertEquals(evidenceDeleted(this, second, reason), entries[1].payload)
            entries.assertSameActor()
        }
    }

    @Test
    fun `Deleting a validation stamp writes, run after run, evidence deleted before validation deleted`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    val other = validationStamp()
                    build {
                        val run1 = validate(vs)
                        val run2 = validate(vs)
                        val kept = validate(other)
                        val evidence1 = run1.upload(pdf())
                        val evidence2 = run2.upload(pdf())
                        kept.upload(pdf())
                        val seq = lastSeq()

                        structureService.deleteValidationStamp(vs.id)

                        val entries = trailAfter(seq)
                        assertEquals(
                            listOf(
                                TrailEntryTypes.EVIDENCE_DELETED,
                                TrailEntryTypes.VALIDATION_DELETED,
                                TrailEntryTypes.EVIDENCE_DELETED,
                                TrailEntryTypes.VALIDATION_DELETED,
                            ),
                            entries.map { it.type },
                        )
                        val reason = "cascade/validation-stamp-deleted"
                        assertEquals(evidenceDeleted(run1, evidence1, reason), entries[0].payload)
                        assertEquals(run1.id(), entries[1].payload.path("validationRun").path("id").asInt())
                        assertEquals(evidenceDeleted(run2, evidence2, reason), entries[2].payload)
                        assertEquals(run2.id(), entries[3].payload.path("validationRun").path("id").asInt())
                        entries.assertSameActor()
                        assertNull(
                            eventQueryService.getLastEvent(this, AuditTrailEvents.EVIDENCE_DELETED),
                            "No event for the evidence going with its run",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Verification with the evidence is intact once the evidence of a deleted validation run is swept`() {
        validationRun {
            val evidence = upload(pdf())
            asAdmin { structureService.deleteValidationRun(this) }
            val lastModified = client.s3.headObject(
                HeadObjectRequest.builder().bucket(client.bucket).key("blobs/${evidence.sha256}").build()
            ).lastModified()
            evidenceBlobCollector.sweep(writtenBefore = lastModified.plusSeconds(1))
            assertFalse(exists("blobs/${evidence.sha256}"), "Blob swept")

            val verification = asAdmin { trailVerificationService.verify(build, includeEvidence = true) }
            assertTrue(verification.chainIntact)
            assertEquals(emptyList(), verification.missingEvidence)
            assertEquals(emptyList(), verification.alteredEvidence)
        }
    }

    @Test
    fun `No evidence deleted is written while the licence is off`() {
        validationRun {
            upload(pdf())
            val seq = build.lastSeq()
            withoutTrail { asAdmin { structureService.deleteValidationRun(this) } }
            assertEquals(emptyList(), build.trailAfter(seq))
        }
    }

    @Test
    fun `No evidence deleted is written for a validation stamp deleted while the licence is off`() {
        validationRun {
            upload(pdf())
            val seq = build.lastSeq()
            withoutTrail { asAdmin { structureService.deleteValidationStamp(validationStamp.id) } }
            assertEquals(emptyList(), build.trailAfter(seq))
        }
    }
}
