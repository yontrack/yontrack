package net.nemerosa.ontrack.extension.findings.events

import net.nemerosa.ontrack.extension.findings.ingestion.FindingExposureTransitionType
import net.nemerosa.ontrack.extension.findings.ingestion.FindingTransition
import net.nemerosa.ontrack.model.events.*

/**
 * Events about the exposure of the findings on the branches, the branch being their entity: a
 * subscription on a branch sees the findings of this branch, a subscription on the project those
 * of all its branches.
 *
 * There is no event per observation, only per change of exposure on a branch, all the stamps of
 * the branch rolled up.
 */
object FindingsEvents {

    const val EVENT_SEVERITY = "SEVERITY"
    const val EVENT_EXTERNAL_ID = "EXTERNAL_ID"
    const val EVENT_LOCATION = "LOCATION"
    const val EVENT_SCANNER = "SCANNER"
    const val EVENT_KIND = "KIND"
    const val EVENT_REOPENED = "REOPENED"

    private val findingContext = eventContext(
        eventProject("Project of the finding"),
        eventBranch("Branch the finding is exposed on, or resolved on"),
        eventValue(
            EVENT_SEVERITY,
            "Severity of the finding: CRITICAL, HIGH, MEDIUM, LOW or UNKNOWN. As observed by the scan for a new finding, as last observed on the branch for a resolved one."
        ),
        eventValue(EVENT_EXTERNAL_ID, "Identifier given by the scanner to the finding (a CVE, a rule ID…)"),
        eventValue(EVENT_LOCATION, "Where the finding is (a package, a file, a URL…), without any version. May be empty."),
        eventValue(EVENT_SCANNER, "Name of the scanner which reported the finding"),
        eventValue(EVENT_KIND, "Kind of scan which reported the finding: IMAGE, CODE, SECRETS, DAST, DEPENDENCIES or OTHER"),
        eventValue(
            EVENT_REOPENED,
            "true when the finding was known on the branch before (resolved there, or accepted there), false otherwise"
        ),
    )

    val SECURITY_FINDING_NEW: EventType = SimpleEventType(
        id = "security_finding_new",
        template = $$"""
            New ${$$EVENT_SEVERITY} finding ${$$EVENT_EXTERNAL_ID} reported by ${$$EVENT_SCANNER} on ${project}/${branch}, at ${$$EVENT_LOCATION}.
        """.trimIndent(),
        description = "When a finding becomes exposed on a branch where it was not, without acceptance. " +
                "This includes a return after its resolution on the branch, or after its acceptance, " +
                "flagged by REOPENED being true. A finding first seen already accepted is not new.",
        context = findingContext,
    )

    val SECURITY_FINDING_RESOLVED: EventType = SimpleEventType(
        id = "security_finding_resolved",
        template = $$"""
            Finding ${$$EVENT_EXTERNAL_ID} (${$$EVENT_SEVERITY}) reported by ${$$EVENT_SCANNER} is resolved on ${project}/${branch}, at ${$$EVENT_LOCATION}.
        """.trimIndent(),
        description = "When a finding exposed on a branch is no longer reported by the latest scan of this branch. " +
                "The deletion of a branch resolves nothing.",
        context = findingContext,
    )

    /**
     * Event for a transition of a finding on a branch.
     */
    fun event(transition: FindingTransition): Event =
        Event.of(
            when (transition.type) {
                FindingExposureTransitionType.NEW -> SECURITY_FINDING_NEW
                FindingExposureTransitionType.RESOLVED -> SECURITY_FINDING_RESOLVED
            }
        )
            .withBranch(transition.branch)
            .with(EVENT_SEVERITY, transition.severity.name)
            .with(EVENT_EXTERNAL_ID, transition.finding.externalId)
            .with(EVENT_LOCATION, transition.finding.location)
            .with(EVENT_SCANNER, transition.finding.scanner)
            .with(EVENT_KIND, transition.finding.kind.name)
            .with(EVENT_REOPENED, transition.reopened.toString())
            .build()
}
