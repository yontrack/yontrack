package net.nemerosa.ontrack.extension.audittrail.events

import net.nemerosa.ontrack.extension.audittrail.verification.TrailVerification
import net.nemerosa.ontrack.model.events.*
import net.nemerosa.ontrack.model.structure.Build

/**
 * Notifiable events of the audit trail, the build being their entity: a subscription on a build,
 * its branch or its project sees them.
 */
object AuditTrailEvents {

    const val EVENT_FIRST_BROKEN_SEQ = "FIRST_BROKEN_SEQ"
    const val EVENT_REASON = "REASON"

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
