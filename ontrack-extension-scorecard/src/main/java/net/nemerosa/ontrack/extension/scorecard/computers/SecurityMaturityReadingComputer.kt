package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingComputer
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingOutcome
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSubject
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityCoverage
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityGating
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityRunSample
import net.nemerosa.ontrack.extension.scorecard.samples.SecuritySamples
import net.nemerosa.ontrack.extension.scorecard.samples.SecurityStamp
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.springframework.stereotype.Component

/**
 * `security.maturity`: how far a project has climbed the ladder of its security scans, read on the
 * branches in scope whatever the marker. The rung is the value:
 *
 * - **0** none: no scan in the window
 * - **1** reported: a scan in the window
 * - **2** covered: every kind the set expects has a scan fresher than its freshness — with no
 *   expected kind, as with no estate, some scan is fresh
 * - **3** gating: a security stamp is required by a promotion level, or a scan was created `FAILED`
 *   in the window
 *
 * Each rung needs the ones below it. The reading is never unknown: no scan reads 0.
 */
@Component
class SecurityMaturityReadingComputer(
    private val securitySamples: SecuritySamples,
    private val securityCoverages: SecurityCoverages,
    private val securityGating: SecurityGating,
) : ReadingComputer {

    override val key: String = ReadingKeys.SECURITY_MATURITY

    override fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome {
        val branches = subject.scope.branches
        val coverage = securityCoverages.coverage(subject.set)
        // Fresh scans may be older than the window
        val start = minOf(window.start, window.end.minusDays(coverage.freshnessDays.toLong()))
        val scans = securitySamples.scans(branches, Interval(start, window.end))
        val stamps = securitySamples.securityStamps(branches)
        return aggregate(window, coverage, scans, securityGating.requiredStamps(stamps))
    }

    companion object {

        /**
         * The rung of the project.
         *
         * Details: `count` (scans in the window), `reported`, `covered` and `gating` (whether each
         * rung holds on its own), `expectedKinds` and `freshnessDays` (what covered means for the
         * set), `freshKinds` (kinds with a fresh scan), `missingKinds` (expected kinds with none),
         * `failedScans` (scans created `FAILED` in the window), `requiredStamps` (security stamps required
         * by a promotion level) and `lastScan` (time of the latest scan in the window).
         *
         * @param window Window of the reading
         * @param coverage What covered means for the set
         * @param scans Scans of the window, and the older ones which may still be fresh
         * @param requiredStamps Security stamps required by a promotion level
         */
        fun aggregate(
            window: Interval,
            coverage: SecurityCoverage,
            scans: List<SecurityRunSample>,
            requiredStamps: List<SecurityStamp>,
        ): ReadingOutcome {
            val inWindow = scans.filter { it.time in window }
            val freshSince = window.end.minusDays(coverage.freshnessDays.toLong())
            val fresh = scans.filter { it.time >= freshSince && it.time < window.end }
            val freshKinds = fresh.flatMap { it.kinds }.toSortedSet()
            val missingKinds = coverage.expectedKinds.filter { it !in freshKinds }.sorted()
            val failed = inWindow.count { it.status == ValidationRunStatusID.FAILED }

            val reported = inWindow.isNotEmpty()
            val covered = if (coverage.expectedKinds.isEmpty()) {
                fresh.isNotEmpty()
            } else {
                missingKinds.isEmpty()
            }
            val gating = requiredStamps.isNotEmpty() || failed > 0

            val rung = when {
                !reported -> 0
                !covered -> 1
                !gating -> 2
                else -> 3
            }
            return ReadingOutcome.measured(
                rung.toDouble(),
                mapOf(
                    "count" to inWindow.size,
                    "reported" to reported,
                    "covered" to covered,
                    "gating" to gating,
                    "expectedKinds" to coverage.expectedKinds.sorted().map { it.name },
                    "freshnessDays" to coverage.freshnessDays,
                    "freshKinds" to freshKinds.map { it.name },
                    "missingKinds" to missingKinds.map { it.name },
                    "failedScans" to failed,
                    "requiredStamps" to requiredStamps.map { it.name }.distinct().sorted(),
                    "lastScan" to inWindow.maxOfOrNull { it.time },
                )
            )
        }
    }
}
