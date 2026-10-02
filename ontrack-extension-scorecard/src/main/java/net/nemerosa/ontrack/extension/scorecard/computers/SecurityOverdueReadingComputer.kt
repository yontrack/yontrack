package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.findings.model.FindingSeverity
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingComputer
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingOutcome
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSubject
import net.nemerosa.ontrack.extension.scorecard.model.EstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityFindingSample
import net.nemerosa.ontrack.extension.scorecard.samples.SecuritySamples
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityTargets
import org.springframework.stereotype.Component

/**
 * `security.overdue`: the open CRITICAL findings older than the CRITICAL target of the estate, plus
 * the open HIGH findings older than its HIGH target, at the end of the window. The age of a finding
 * runs from its first observation.
 *
 * Read on the branches in scope whatever the marker. With no target — the no-estate set, or an
 * estate with neither a CRITICAL nor a HIGH target — the reading is `UNKNOWN (NO_TARGET)`; with one
 * target only, the findings of the other severity are not judged.
 *
 * Accepted findings are neither open nor resolved: they are counted apart, in the details.
 */
@Component
class SecurityOverdueReadingComputer(
    private val securitySamples: SecuritySamples,
) : ReadingComputer {

    override val key: String = ReadingKeys.SECURITY_OVERDUE

    override fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome =
        aggregate(
            window,
            targets(subject.set),
            securitySamples.remediationFindings(subject.project, subject.scope.branches, window),
        )

    private fun targets(set: ReadingSet): SecurityTargets = when (set) {
        NoEstateReadingSet -> SecurityTargets.NONE
        is EstateReadingSet -> SecurityTargets(
            criticalDays = set.estate.security.criticalTargetDays,
            highDays = set.estate.security.highTargetDays,
        )
    }

    companion object {

        /**
         * Number of overdue findings in the value.
         *
         * Details: `overdueCritical` and `overdueHigh` (`null` for a severity with no target),
         * `openCritical` and `openHigh`, `criticalTargetDays` and `highTargetDays`, `overdueSince`
         * (first observation of the oldest overdue finding) and `accepted`, the number of CRITICAL
         * and HIGH findings accepted at the end of the window.
         *
         * @param window Window of the reading, whose end the age of the findings is measured at
         * @param targets Remediation targets of the set
         * @param findings CRITICAL and HIGH findings of the project
         */
        fun aggregate(
            window: Interval,
            targets: SecurityTargets,
            findings: List<SecurityFindingSample>,
        ): ReadingOutcome {
            val open = findings.filter { it.state == FindingState.OPEN }
            val openCritical = open.filter { it.severity == FindingSeverity.CRITICAL }
            val openHigh = open.filter { it.severity == FindingSeverity.HIGH }

            fun overdue(findings: List<SecurityFindingSample>, days: Int?): List<SecurityFindingSample>? =
                days?.let { window.end.minusDays(it.toLong()) }?.let { limit ->
                    findings.filter { it.firstSeen < limit }
                }

            val overdueCritical = overdue(openCritical, targets.criticalDays)
            val overdueHigh = overdue(openHigh, targets.highDays)
            val details = mapOf(
                "overdueCritical" to overdueCritical?.size,
                "overdueHigh" to overdueHigh?.size,
                "openCritical" to openCritical.size,
                "openHigh" to openHigh.size,
                "criticalTargetDays" to targets.criticalDays,
                "highTargetDays" to targets.highDays,
                "overdueSince" to ((overdueCritical ?: emptyList()) + (overdueHigh ?: emptyList()))
                    .minOfOrNull { it.firstSeen },
                "accepted" to findings.count { it.state == FindingState.ACCEPTED },
            )
            return if (overdueCritical == null && overdueHigh == null) {
                ReadingOutcome.unknown(ReadingUnknownReason.NO_TARGET, details)
            } else {
                ReadingOutcome.measured(
                    ((overdueCritical?.size ?: 0) + (overdueHigh?.size ?: 0)).toDouble(),
                    details,
                )
            }
        }
    }
}
