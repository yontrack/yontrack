package net.nemerosa.ontrack.extension.audittrail.events

import net.nemerosa.ontrack.extension.audittrail.evidence.Evidence
import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerification
import net.nemerosa.ontrack.model.events.*
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.ValidationRun

/**
 * Notifiable events of the audit trail, about a build or one of its validation runs: a
 * subscription on the build, its branch or its project sees them.
 */
object AuditTrailEvents {

    const val EVENT_FIRST_BROKEN_SEQ = "FIRST_BROKEN_SEQ"
    const val EVENT_REASON = "REASON"
    const val EVENT_EVIDENCE_ID = "EVIDENCE_ID"
    const val EVENT_EVIDENCE_FILE_NAME = "EVIDENCE_FILE_NAME"
    const val EVENT_EVIDENCE_MEDIA_TYPE = "EVIDENCE_MEDIA_TYPE"
    const val EVENT_EVIDENCE_SIZE = "EVIDENCE_SIZE"
    const val EVENT_EVIDENCE_SHA256 = "EVIDENCE_SHA256"

    val EVIDENCE_ATTACHED: EventType = SimpleEventType(
        id = "evidence.attached",
        template = $$"""
            Evidence ${$$EVENT_EVIDENCE_FILE_NAME} has been attached to the ${validationStamp} validation ${validationRun} of build ${build} in branch ${branch} of ${project}.
        """.trimIndent(),
        description = "When an evidence - a file - is attached to a validation run. The evidence is referenced " +
                "by an evidence.attached entry of the trail of the build, with its SHA-256.",
        context = eventContext(
            eventProject("Project of the build"),
            eventBranch("Branch of the build"),
            eventBuild("Build of the validation run"),
            eventValidationStamp("Validation stamp of the validation run"),
            eventValidationRun("Validation run the evidence is attached to"),
            eventValue(EVENT_EVIDENCE_ID, "ID of the evidence"),
            eventValue(EVENT_EVIDENCE_FILE_NAME, "Name of the file of the evidence"),
            eventValue(EVENT_EVIDENCE_MEDIA_TYPE, "Media type of the evidence, as declared"),
            eventValue(EVENT_EVIDENCE_SIZE, "Size of the evidence, in bytes"),
            eventValue(EVENT_EVIDENCE_SHA256, "SHA-256 of the content of the evidence, in lowercase hexadecimal"),
        ),
    )

    val EVIDENCE_DELETED: EventType = SimpleEventType(
        id = "evidence.deleted",
        template = $$"""
            Evidence ${$$EVENT_EVIDENCE_FILE_NAME} has been deleted from the ${validationStamp} validation ${validationRun} of build ${build} in branch ${branch} of ${project}.
        """.trimIndent(),
        description = "When an evidence is deleted from a validation run. The deletion is recorded by an " +
                "evidence.deleted entry of the trail of the build; the evidence is kept, marked as deleted, " +
                "and its content is removed from the storage unless another evidence has the same content.",
        context = eventContext(
            eventProject("Project of the build"),
            eventBranch("Branch of the build"),
            eventBuild("Build of the validation run"),
            eventValidationStamp("Validation stamp of the validation run"),
            eventValidationRun("Validation run the evidence was attached to"),
            eventValue(EVENT_EVIDENCE_ID, "ID of the evidence"),
            eventValue(EVENT_EVIDENCE_FILE_NAME, "Name of the file of the evidence"),
            eventValue(EVENT_EVIDENCE_MEDIA_TYPE, "Media type of the evidence, as declared"),
            eventValue(EVENT_EVIDENCE_SIZE, "Size of the evidence, in bytes"),
            eventValue(EVENT_EVIDENCE_SHA256, "SHA-256 of the content of the evidence, in lowercase hexadecimal"),
        ),
    )

    val TRAIL_VERIFICATION_FAILED: EventType = SimpleEventType(
        id = "trail.verification.failed",
        template = $$"""
            The trail of build ${build} of ${project}/${branch} fails its verification at entry ${$$EVENT_FIRST_BROKEN_SEQ}: ${$$EVENT_REASON}
        """.trimIndent(),
        description = "When the daily verification of the trails finds that the trail of a build was tampered with: " +
                "its chain is broken, or an endorsement is invalid. Only the trails which gained entries since the " +
                "previous verification are verified, and their evidence is not re-hashed.",
        context = eventContext(
            eventProject("Project of the build"),
            eventBranch("Branch of the build"),
            eventBuild("Build whose trail fails its verification"),
            eventValue(
                EVENT_FIRST_BROKEN_SEQ,
                "Position, from 1, of the first entry failing a check: of the chain, or of an endorsement when the chain is intact"
            ),
            eventValue(EVENT_REASON, "Check failed by this entry, for a human"),
        ),
    )

    /**
     * Event for an evidence attached to a validation run.
     *
     * @param validationRun Validation run the evidence is attached to
     * @param evidence Attached evidence
     * @param signature Who attached it, and when
     */
    fun evidenceAttached(validationRun: ValidationRun, evidence: Evidence, signature: Signature): Event =
        evidenceEvent(EVIDENCE_ATTACHED, validationRun, evidence, signature)

    /**
     * Event for an evidence deleted from a validation run.
     *
     * @param validationRun Validation run the evidence was attached to
     * @param evidence Deleted evidence
     * @param signature Who deleted it, and when
     */
    fun evidenceDeleted(validationRun: ValidationRun, evidence: Evidence, signature: Signature): Event =
        evidenceEvent(EVIDENCE_DELETED, validationRun, evidence, signature)

    private fun evidenceEvent(eventType: EventType, validationRun: ValidationRun, evidence: Evidence, signature: Signature): Event =
        Event.of(eventType)
            .withValidationRun(validationRun)
            .with(signature)
            .with(EVENT_EVIDENCE_ID, evidence.id.toString())
            .with(EVENT_EVIDENCE_FILE_NAME, evidence.fileName)
            .with(EVENT_EVIDENCE_MEDIA_TYPE, evidence.mediaType)
            .with(EVENT_EVIDENCE_SIZE, evidence.size.toString())
            .with(EVENT_EVIDENCE_SHA256, evidence.sha256)
            .build()

    /**
     * Event for the failed verification of the trail of a build.
     *
     * @param build Build whose trail was verified
     * @param verification Failed verification of its trail
     */
    fun trailVerificationFailed(build: Build, verification: TrailVerification): Event {
        // The chain first: an endorsement is only checked against the hash it signs
        val problem = verification.problems.firstOrNull { it.type.chain }
            ?: verification.problems.first()
        return Event.of(TRAIL_VERIFICATION_FAILED)
            .withBuild(build)
            .with(EVENT_FIRST_BROKEN_SEQ, problem.seq.toString())
            .with(EVENT_REASON, problem.message)
            .build()
    }
}
