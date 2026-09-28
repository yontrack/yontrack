package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.model.structure.ValidationRunStatusID

/**
 * A build with at least one run on a test stamp, and what its runs say.
 *
 * @property branchId Branch of the build
 * @property buildId ID of the build
 * @property passed Has the latest run of the build passed on every test stamp it was run on?
 * @property flaky Has the build, on some test stamp, a `FAILED` run followed by a `PASSED` run?
 */
data class TestBuildSample(
    val branchId: Int,
    val buildId: Int,
    val passed: Boolean,
    val flaky: Boolean,
)

/**
 * Reading the test runs build by build.
 */
object TestBuilds {

    /**
     * Groups the runs by build, in the order of the builds, and reads each build's runs stamp by stamp,
     * in the order of the runs.
     */
    fun of(runs: List<TestRunSample>): List<TestBuildSample> =
        runs.groupBy { it.branchId to it.buildId }
            .map { (build, buildRuns) ->
                val byStamp = buildRuns.groupBy { it.stampId }.values.map { stampRuns ->
                    stampRuns.sortedBy { it.runId }.map { it.status }
                }
                TestBuildSample(
                    branchId = build.first,
                    buildId = build.second,
                    passed = byStamp.all { it.last() == ValidationRunStatusID.PASSED },
                    flaky = byStamp.any { statuses -> isFlaky(statuses) },
                )
            }

    /**
     * A `FAILED` status followed, immediately or not, by a `PASSED` one.
     */
    private fun isFlaky(statuses: List<String>): Boolean {
        val firstFailure = statuses.indexOf(ValidationRunStatusID.FAILED)
        return firstFailure >= 0 &&
                statuses.subList(firstFailure + 1, statuses.size).contains(ValidationRunStatusID.PASSED)
    }
}
