package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingComputer
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingOutcome
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingSubject
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.samples.TestRunSample
import net.nemerosa.ontrack.extension.scorecard.samples.TestSamples
import net.nemerosa.ontrack.extension.scorecard.samples.TestStamp
import org.springframework.stereotype.Component

/**
 * `quality.testPassRate`: among the builds with at least one run on a test stamp, the share whose
 * **latest** run on every test stamp passed, as a percentage.
 *
 * Read on the branches in scope, whatever the marker: the builds created in the window, with their
 * runs created before its end. A run passes when it was created `PASSED`.
 */
@Component
class TestPassRateReadingComputer(
    private val testSamples: TestSamples,
) : ReadingComputer {

    override val key: String = ReadingKeys.QUALITY_TEST_PASS_RATE

    override fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome {
        val stamps = testSamples.testStamps(subject.scope.branches)
        return aggregate(stamps, testSamples.runs(stamps, window))
    }

    companion object {
        /**
         * Percentage of the builds which passed.
         *
         * Details: `count` (builds with a test run), `passed` (those which passed) and `testStamps`
         * (names of the test stamps in scope).
         */
        fun aggregate(stamps: List<TestStamp>, runs: List<TestRunSample>): ReadingOutcome =
            TestReadings.aggregate(stamps, runs, "passed") { it.passed }
    }
}
