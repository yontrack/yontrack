package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.scorecard.engine.ReadingOutcome
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.TestBuildSample
import net.nemerosa.ontrack.extension.scorecard.samples.TestBuilds
import net.nemerosa.ontrack.extension.scorecard.samples.TestRunSample
import net.nemerosa.ontrack.extension.scorecard.samples.TestStamp

/**
 * What the test readings share: the share of the builds with a test run meeting a condition.
 */
internal object TestReadings {

    /**
     * @param stamps Test stamps in scope, `NO_TEST_STAMP` when empty
     * @param runs Runs on these stamps, `NO_SAMPLES` when empty
     * @param name Name of the count of the builds meeting the condition, in the details
     * @param condition Condition on a build
     */
    fun aggregate(
        stamps: List<TestStamp>,
        runs: List<TestRunSample>,
        name: String,
        condition: (TestBuildSample) -> Boolean,
    ): ReadingOutcome {
        val testStamps = stamps.map { it.name }.distinct().sorted()
        if (stamps.isEmpty()) {
            return ReadingOutcome.unknown(
                ReadingUnknownReason.NO_TEST_STAMP,
                mapOf("testStamps" to testStamps)
            )
        }
        val builds = TestBuilds.of(runs)
        val matching = builds.count(condition)
        val details = mapOf(
            "count" to builds.size,
            name to matching,
            "testStamps" to testStamps,
        )
        return if (builds.isEmpty()) {
            ReadingOutcome.unknown(ReadingUnknownReason.NO_SAMPLES, details)
        } else {
            ReadingOutcome.measured(100.0 * matching / builds.size, details)
        }
    }
}
