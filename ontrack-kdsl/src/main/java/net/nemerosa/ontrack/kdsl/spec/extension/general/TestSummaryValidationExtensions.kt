package net.nemerosa.ontrack.kdsl.spec.extension.general

import net.nemerosa.ontrack.kdsl.spec.Branch
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.ValidationRun
import net.nemerosa.ontrack.kdsl.spec.ValidationStamp
import java.time.LocalDateTime

/**
 * FQCN of the test summary validation data type, the `tests` stamps.
 */
const val TEST_SUMMARY_VALIDATION_DATA_TYPE = "net.nemerosa.ontrack.extension.general.validation.TestSummaryValidationDataType"

/**
 * Creates a validation stamp of the test summary data type: its runs carry the counts of passed,
 * skipped and failed tests, and any failed test makes a failed run.
 *
 * @param name Name of the validation stamp
 * @param description Description of the validation stamp
 * @param warningIfSkipped A run with skipped tests is a warning
 * @param failWhenNoResults A run with no test at all is a failure
 * @return Created validation stamp
 */
fun Branch.createTestSummaryValidationStamp(
    name: String,
    description: String = "",
    warningIfSkipped: Boolean = false,
    failWhenNoResults: Boolean = false,
): ValidationStamp = createValidationStamp(
    name = name,
    description = description,
    dataType = TEST_SUMMARY_VALIDATION_DATA_TYPE,
    dataTypeConfig = mapOf(
        "warningIfSkipped" to warningIfSkipped,
        "failWhenNoResults" to failWhenNoResults,
    ),
)

/**
 * Validates this build with a test summary.
 *
 * @param validation Name of the validation stamp
 * @param description Description of the run
 * @param status Status of the run, computed from the counts and the stamp when null
 * @param testSummary Counts of the tests
 * @param dateTime Time of the run, to backdate it. The moment of the call when null.
 * @return Created validation run
 */
fun Build.validateWithTestSummary(
    validation: String,
    description: String = "",
    status: String? = null,
    testSummary: TestSummary,
    dateTime: LocalDateTime? = null,
): ValidationRun = validateWithData(
    validationStamp = validation,
    dataTypeId = TEST_SUMMARY_VALIDATION_DATA_TYPE,
    data = testSummary,
    status = status,
    description = description,
    dateTime = dateTime,
)

data class TestSummary(
    val passed: Int,
    val skipped: Int,
    val failed: Int,
)
