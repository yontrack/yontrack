package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.model.structure.Branch

/**
 * Sample functions of the test readings, read on the branches in scope whatever the marker.
 *
 * Test stamps are the validation stamps whose data type is the test summary
 * (`TestSummaryValidationDataType`).
 */
interface TestSamples {

    /**
     * Test stamps of the branches, ordered by branch and stamp order.
     */
    fun testStamps(branches: Collection<Branch>): List<TestStamp>

    /**
     * Runs on the test stamps of the builds created in the interval, the start of the interval
     * included, its end excluded. Only the runs created before the end of the interval are kept.
     *
     * Runs are ordered by build and then by run.
     */
    fun runs(stamps: Collection<TestStamp>, interval: Interval): List<TestRunSample>
}
