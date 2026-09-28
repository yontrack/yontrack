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
 * `quality.testFlakiness`: among the builds with at least one run on a test stamp, the share where
 * some test stamp has a `FAILED` run followed by a `PASSED` run, as a percentage.
 *
 * Read on the branches in scope, whatever the marker, on the same builds as the
 * [pass rate][TestPassRateReadingComputer]. The status of a run is the one it was created with.
 */
@Component
class TestFlakinessReadingComputer(
    private val testSamples: TestSamples,
) : ReadingComputer {

    override val key: String = ReadingKeys.QUALITY_TEST_FLAKINESS

    override fun compute(subject: ReadingSubject, window: Interval): ReadingOutcome {
        val stamps = testSamples.testStamps(subject.scope.branches)
        return aggregate(stamps, testSamples.runs(stamps, window))
    }

    companion object {
        /**
         * Percentage of the builds which were flaky.
         *
         * Details: `count` (builds with a test run), `flaky` (those which were flaky) and `testStamps`
         * (names of the test stamps in scope).
         */
        fun aggregate(stamps: List<TestStamp>, runs: List<TestRunSample>): ReadingOutcome =
            TestReadings.aggregate(stamps, runs, "flaky") { it.flaky }
    }
}
