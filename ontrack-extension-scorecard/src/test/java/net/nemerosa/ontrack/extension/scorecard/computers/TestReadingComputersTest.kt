package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.extension.scorecard.samples.TestRunSample
import net.nemerosa.ontrack.extension.scorecard.samples.TestStamp
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `quality.testPassRate` and `quality.testFlakiness`, aggregated from plain lists of runs.
 */
class TestReadingComputersTest {

    private val ref = LocalDateTime.of(2026, 9, 1, 0, 0)

    private val unit = TestStamp(id = 10, branchId = 1, name = "unit")
    private val integration = TestStamp(id = 11, branchId = 1, name = "integration")
    private val stamps = listOf(unit, integration)

    private var nextRunId = 1

    private fun run(build: Int, stamp: TestStamp, status: String) = (nextRunId++).let { runId ->
        TestRunSample(
            branchId = stamp.branchId,
            buildId = build,
            stampId = stamp.id,
            runId = runId,
            status = status,
            time = ref.plusHours(runId.toLong()),
        )
    }

    private fun passed(build: Int, stamp: TestStamp) = run(build, stamp, ValidationRunStatusID.PASSED)
    private fun failed(build: Int, stamp: TestStamp) = run(build, stamp, ValidationRunStatusID.FAILED)
    private fun warning(build: Int, stamp: TestStamp) = run(build, stamp, ValidationRunStatusID.WARNING)

    // Pass rate

    @Test
    fun `Pass rate is the share of the builds whose latest run passed on every test stamp`() {
        val outcome = TestPassRateReadingComputer.aggregate(
            stamps,
            listOf(
                // Passed on both
                passed(1, unit), passed(1, integration),
                // Failed on one
                passed(2, unit), failed(2, integration),
                // Only one stamp run, passed
                passed(3, unit),
                // Warning is not a pass
                warning(4, unit),
            ),
        )
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertNull(outcome.unknownReason)
        assertEquals(50.0, outcome.value)
        assertEquals(4, outcome.details["count"])
        assertEquals(2, outcome.details["passed"])
        assertEquals(listOf("integration", "unit"), outcome.details["testStamps"])
    }

    @Test
    fun `Pass rate reads the latest run of each stamp`() {
        val outcome = TestPassRateReadingComputer.aggregate(
            stamps,
            listOf(
                // Failed, then passed: passes
                failed(1, unit), passed(1, unit),
                // Passed, then failed: fails
                passed(2, unit), failed(2, unit),
            ),
        )
        assertEquals(50.0, outcome.value)
        assertEquals(2, outcome.details["count"])
        assertEquals(1, outcome.details["passed"])
    }

    @Test
    fun `Pass rate with no run gives NO_SAMPLES`() {
        val outcome = TestPassRateReadingComputer.aggregate(stamps, emptyList())
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_SAMPLES, outcome.unknownReason)
        assertNull(outcome.value)
        assertEquals(0, outcome.details["count"])
        assertEquals(listOf("integration", "unit"), outcome.details["testStamps"])
    }

    @Test
    fun `Pass rate with no test stamp gives NO_TEST_STAMP`() {
        val outcome = TestPassRateReadingComputer.aggregate(emptyList(), emptyList())
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_TEST_STAMP, outcome.unknownReason)
        assertNull(outcome.value)
        assertEquals(emptyList<String>(), outcome.details["testStamps"])
    }

    // Flakiness

    @Test
    fun `Flakiness is the share of the builds where a stamp has a FAILED run followed by a PASSED run`() {
        val outcome = TestFlakinessReadingComputer.aggregate(
            stamps,
            listOf(
                // Failed then passed on one stamp: flaky
                failed(1, unit), passed(1, unit), passed(1, integration),
                // Passed then failed: not flaky
                passed(2, unit), failed(2, unit),
                // Failed only: not flaky
                failed(3, unit), failed(3, unit),
                // Passed only: not flaky
                passed(4, integration),
            ),
        )
        assertEquals(ReadingBasis.MEASURED, outcome.basis)
        assertNull(outcome.unknownReason)
        assertEquals(25.0, outcome.value)
        assertEquals(4, outcome.details["count"])
        assertEquals(1, outcome.details["flaky"])
        assertEquals(listOf("integration", "unit"), outcome.details["testStamps"])
    }

    @Test
    fun `A PASSED run following a FAILED one later on is flaky, whatever runs are between`() {
        val outcome = TestFlakinessReadingComputer.aggregate(
            stamps,
            listOf(
                failed(1, unit), warning(1, unit), failed(1, unit), passed(1, unit), failed(1, unit),
            ),
        )
        assertEquals(100.0, outcome.value)
        assertEquals(1, outcome.details["flaky"])
    }

    @Test
    fun `A FAILED run and a PASSED run on different stamps are not flaky`() {
        val outcome = TestFlakinessReadingComputer.aggregate(
            stamps,
            listOf(failed(1, unit), passed(1, integration)),
        )
        assertEquals(0.0, outcome.value)
        assertEquals(1, outcome.details["count"])
        assertEquals(0, outcome.details["flaky"])
    }

    @Test
    fun `A FAILED run and a PASSED run on different builds are not flaky`() {
        val outcome = TestFlakinessReadingComputer.aggregate(
            stamps,
            listOf(failed(1, unit), passed(2, unit)),
        )
        assertEquals(0.0, outcome.value)
        assertEquals(2, outcome.details["count"])
    }

    @Test
    fun `Flakiness with no run gives NO_SAMPLES`() {
        val outcome = TestFlakinessReadingComputer.aggregate(stamps, emptyList())
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_SAMPLES, outcome.unknownReason)
        assertEquals(0, outcome.details["count"])
    }

    @Test
    fun `Flakiness with no test stamp gives NO_TEST_STAMP`() {
        val outcome = TestFlakinessReadingComputer.aggregate(emptyList(), emptyList())
        assertEquals(ReadingBasis.UNKNOWN, outcome.basis)
        assertEquals(ReadingUnknownReason.NO_TEST_STAMP, outcome.unknownReason)
    }
}
