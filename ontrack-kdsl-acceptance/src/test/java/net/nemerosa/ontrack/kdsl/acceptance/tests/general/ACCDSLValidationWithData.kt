package net.nemerosa.ontrack.kdsl.acceptance.tests.general

import net.nemerosa.ontrack.kdsl.acceptance.tests.AbstractACCDSLTestSupport
import net.nemerosa.ontrack.kdsl.spec.extension.general.TEST_SUMMARY_VALIDATION_DATA_TYPE
import net.nemerosa.ontrack.kdsl.spec.extension.general.TestSummary
import net.nemerosa.ontrack.kdsl.spec.extension.general.createTestSummaryValidationStamp
import net.nemerosa.ontrack.kdsl.spec.extension.general.validateWithTestSummary
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Validating a build with the data of a validation data type AND a date, which is what a seeded or
 * replayed history needs: the typed mutations of the data types take no date.
 */
class ACCDSLValidationWithData : AbstractACCDSLTestSupport() {

    @Test
    fun `Validating with a test summary at a given time`() {
        val time = LocalDateTime.now(ZoneOffset.UTC).minusDays(10).withNano(0)
        project {
            branch {
                val stamp = createTestSummaryValidationStamp("TESTS")
                build {
                    updateCreationTime(time.minusHours(1))
                    val failed = validateWithTestSummary(
                        validation = stamp.name,
                        testSummary = TestSummary(passed = 10, skipped = 0, failed = 1),
                        dateTime = time,
                    )
                    val passed = validateWithTestSummary(
                        validation = stamp.name,
                        testSummary = TestSummary(passed = 11, skipped = 0, failed = 0),
                        dateTime = time.plusHours(1),
                    )
                    // The status is computed from the counts, and the run has the time it was given
                    assertEquals("FAILED", failed.lastStatus.id)
                    assertEquals(time, failed.time)
                    assertEquals("PASSED", passed.lastStatus.id)
                    assertEquals(time.plusHours(1), passed.time)
                    // ... and carries its data
                    val data = assertNotNull(failed.data)
                    assertEquals(TEST_SUMMARY_VALIDATION_DATA_TYPE, data.type)
                    assertEquals(1, data.data.path("failed").asInt())
                    assertEquals(10, data.data.path("passed").asInt())
                }
            }
        }
    }

    @Test
    fun `Validating with data at the moment of the call`() {
        project {
            branch {
                val stamp = createTestSummaryValidationStamp("TESTS")
                build {
                    val run = validateWithData(
                        validationStamp = stamp.name,
                        dataTypeId = TEST_SUMMARY_VALIDATION_DATA_TYPE,
                        data = mapOf("passed" to 5, "skipped" to 1, "failed" to 0),
                        description = "No date",
                    )
                    assertEquals("PASSED", run.lastStatus.id)
                    assertEquals("No date", run.lastStatus.description)
                    assertNotNull(run.time)
                }
            }
        }
    }
}
