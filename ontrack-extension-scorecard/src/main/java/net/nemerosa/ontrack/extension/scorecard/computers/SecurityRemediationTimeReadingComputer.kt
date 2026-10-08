package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.findings.model.FindingState
import net.nemerosa.ontrack.extension.scorecard.engine.DurationStatistics
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingComputer
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingOutcome
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSubject
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityFindingSample
import net.nemerosa.ontrack.extension.scorecard.samples.SecuritySamples
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * `security.remediationTime`: how long the CRITICAL and HIGH findings of a project stay open.
 *
 * One sample per [exposure episode][net.nemerosa.ontrack.extension.findings.model.FindingExposureEpisode]
 * of these findings on the branches in scope which ends in the window, from its start to its end: a
 * reopened finding gives one sample per fix, the time it stayed resolved left out, and a fix applied
 * on several branches gives one sample. The median, in seconds, goes in the value. Read on the
 * branches in scope whatever the marker, the same in every set: it needs no target.
 *
 * Accepted findings are neither open nor resolved: they are counted apart, in the details.
 */
@Component
class SecurityRemediationTimeReadingComputer(
    private val securitySamples: SecuritySamples,
) : ReadingComputer {

    override val key: String = ReadingKeys.SECURITY_REMEDIATION_TIME

    override fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome =
        aggregate(
            window,
            securitySamples.remediationFindings(subject.project, subject.scope.branches, window),
        )

    companion object {

        /**
         * Median of the remediation times in the value, p90, mean, min, max and count in the
         * details, with `accepted`, the number of CRITICAL and HIGH findings accepted at the end of
         * the window. `NO_SAMPLES` when no episode ended in the window.
         *
         * @param window Window of the reading
         * @param findings CRITICAL and HIGH findings of the project
         */
        fun aggregate(window: Interval, findings: List<SecurityFindingSample>): ReadingOutcome {
            val accepted = mapOf("accepted" to findings.count { it.state == FindingState.ACCEPTED })
            val durations = findings
                .flatMap { it.episodes }
                .mapNotNull { episode -> episode.end?.takeIf { it in window }?.let { episode.start to it } }
                .map { (start, end) -> Duration.between(start, end).seconds.toDouble() }
            val stats = DurationStatistics.of(durations)
            return if (stats == null) {
                ReadingOutcome.unknown(ReadingUnknownReason.NO_SAMPLES, mapOf("count" to 0) + accepted)
            } else {
                ReadingOutcome.measured(stats.median, stats.details + accepted)
            }
        }
    }
}
