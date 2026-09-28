package net.nemerosa.ontrack.extension.scorecard.computers

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationConfig
import net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationData
import net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationDataType
import net.nemerosa.ontrack.extension.scorecard.engine.ReadingEngine
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.extension.scorecard.model.Reading
import net.nemerosa.ontrack.extension.scorecard.model.ReadingBasis
import net.nemerosa.ontrack.extension.scorecard.model.ReadingKeys
import net.nemerosa.ontrack.extension.scorecard.model.ReadingUnknownReason
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.Signature
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The test readings of the no-estate set, computed by the engine on backdated builds and test runs,
 * several runs per build and stamp.
 */
class TestReadingsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var readingEngine: ReadingEngine

    @Autowired
    private lateinit var testSummaryValidationDataType: TestSummaryValidationDataType

    private val now = Time.now.withNano(0)

    private fun Project.readings(): Map<String, Reading> =
        readingEngine.computeProject(NoEstateReadingSet, this)!!.associateBy { it.key }

    private fun Branch.testStamp(name: String): ValidationStamp =
        validationStamp(
            name = name,
            validationDataTypeConfig = testSummaryValidationDataType.config(TestSummaryValidationConfig()),
        )

    private fun Branch.buildAt(time: LocalDateTime): Build =
        build().apply { updateBuildSignature(time = time) }

    /**
     * Test run, `PASSED` or `FAILED` according to its data
     */
    private fun Build.test(stamp: ValidationStamp, failed: Int, time: LocalDateTime): ValidationRun =
        validateWithData(
            stamp,
            validationRunData = TestSummaryValidationData(passed = 10, skipped = 0, failed = failed),
            signature = Signature.of(time, "test"),
        )

    @Test
    fun `Pass rate and flakiness on the latest runs of the builds, several runs per build and stamp`() {
        asAdmin {
            project {
                branch("main") {
                    val unit = testStamp("unit")
                    val integration = testStamp("integration")
                    // Not a test stamp: its runs are not read
                    val lint = validationStamp("lint")

                    // Passed on both stamps, first try
                    buildAt(now.minusDays(30)).apply {
                        test(unit, failed = 0, time = now.minusDays(30).plusHours(1))
                        test(integration, failed = 0, time = now.minusDays(30).plusHours(2))
                        validate(lint, ValidationRunStatusID.STATUS_FAILED, signature = Signature.of(now.minusDays(30).plusHours(3), "test"))
                    }
                    // Failed, then passed on the rerun: passed, and flaky.
                    // The failed run is triaged afterwards: it was still created FAILED.
                    buildAt(now.minusDays(20)).apply {
                        test(unit, failed = 2, time = now.minusDays(20).plusHours(1))
                            .validationStatus(ValidationRunStatusID.STATUS_EXPLAINED, "Flaky test")
                        test(unit, failed = 0, time = now.minusDays(20).plusHours(2))
                        test(integration, failed = 0, time = now.minusDays(20).plusHours(3))
                    }
                    // Passed, then failed on the rerun: not passed, not flaky
                    buildAt(now.minusDays(10)).apply {
                        test(unit, failed = 0, time = now.minusDays(10).plusHours(1))
                        test(unit, failed = 1, time = now.minusDays(10).plusHours(2))
                    }
                    // Failed on integration twice: not passed, not flaky
                    buildAt(now.minusDays(5)).apply {
                        test(unit, failed = 0, time = now.minusDays(5).plusHours(1))
                        test(integration, failed = 3, time = now.minusDays(5).plusHours(2))
                        test(integration, failed = 3, time = now.minusDays(5).plusHours(3))
                    }
                    // No test run: not counted
                    buildAt(now.minusDays(4)).apply {
                        validate(lint, signature = Signature.of(now.minusDays(4).plusHours(1), "test"))
                    }
                    // Created before the window: not counted
                    buildAt(now.minusDays(200)).apply {
                        test(unit, failed = 1, time = now.minusDays(200).plusHours(1))
                    }
                }
                branch("feature") {
                    // Test stamp on another branch: the project has no branch model, so every branch is read
                    val unit = testStamp("unit")
                    buildAt(now.minusDays(3)).apply {
                        test(unit, failed = 1, time = now.minusDays(3).plusHours(1))
                        test(unit, failed = 0, time = now.minusDays(3).plusHours(2))
                    }
                }

                val readings = readings()

                val passRate = readings.getValue(ReadingKeys.QUALITY_TEST_PASS_RATE)
                assertEquals(ReadingBasis.MEASURED, passRate.basis)
                assertNull(passRate.unknownReason)
                assertEquals(60.0, passRate.value)
                assertEquals(5, passRate.details.path("count").asInt())
                assertEquals(3, passRate.details.path("passed").asInt())
                assertEquals(listOf("integration", "unit").asJson(), passRate.details.path("testStamps"))
                assertEquals("ALL_BRANCHES", passRate.details.path("scope").path("kind").asText())
                assertEquals(90L, java.time.Duration.between(passRate.windowStart, passRate.windowEnd).toDays())

                val flakiness = readings.getValue(ReadingKeys.QUALITY_TEST_FLAKINESS)
                assertEquals(ReadingBasis.MEASURED, flakiness.basis)
                assertNull(flakiness.unknownReason)
                assertEquals(40.0, flakiness.value)
                assertEquals(5, flakiness.details.path("count").asInt())
                assertEquals(2, flakiness.details.path("flaky").asInt())
            }
        }
    }

    @Test
    fun `Test readings do not need a marker`() {
        asAdmin {
            project {
                branch("main") {
                    val unit = testStamp("unit")
                    buildAt(now.minusDays(2)).apply {
                        test(unit, failed = 0, time = now.minusDays(2).plusHours(1))
                    }
                }
                val readings = readings()
                assertEquals(
                    ReadingUnknownReason.NO_MARKER,
                    readings.getValue(ReadingKeys.DELIVERY_LEAD_TIME).unknownReason
                )
                val passRate = readings.getValue(ReadingKeys.QUALITY_TEST_PASS_RATE)
                assertEquals(ReadingBasis.MEASURED, passRate.basis)
                assertEquals(100.0, passRate.value)
                val flakiness = readings.getValue(ReadingKeys.QUALITY_TEST_FLAKINESS)
                assertEquals(ReadingBasis.MEASURED, flakiness.basis)
                assertEquals(0.0, flakiness.value)
            }
        }
    }

    @Test
    fun `No test stamp gives NO_TEST_STAMP`() {
        asAdmin {
            project {
                branch("main") {
                    val lint = validationStamp("lint")
                    buildAt(now.minusDays(2)).apply {
                        validate(lint, signature = Signature.of(now.minusDays(2).plusHours(1), "test"))
                    }
                }
                val readings = readings()
                listOf(ReadingKeys.QUALITY_TEST_PASS_RATE, ReadingKeys.QUALITY_TEST_FLAKINESS).forEach { key ->
                    val reading = readings.getValue(key)
                    assertEquals(ReadingBasis.UNKNOWN, reading.basis)
                    assertEquals(ReadingUnknownReason.NO_TEST_STAMP, reading.unknownReason)
                    assertNull(reading.value)
                }
            }
        }
    }

    @Test
    fun `No test run in the window gives NO_SAMPLES`() {
        asAdmin {
            project {
                branch("main") {
                    val unit = testStamp("unit")
                    buildAt(now.minusDays(200)).apply {
                        test(unit, failed = 0, time = now.minusDays(200).plusHours(1))
                    }
                    // In the window, but not tested
                    buildAt(now.minusDays(2))
                }
                val readings = readings()
                listOf(ReadingKeys.QUALITY_TEST_PASS_RATE, ReadingKeys.QUALITY_TEST_FLAKINESS).forEach { key ->
                    val reading = readings.getValue(key)
                    assertEquals(ReadingBasis.UNKNOWN, reading.basis)
                    assertEquals(ReadingUnknownReason.NO_SAMPLES, reading.unknownReason)
                    assertEquals(0, reading.details.path("count").asInt())
                    assertEquals(listOf("unit").asJson(), reading.details.path("testStamps"))
                }
            }
        }
    }
}
