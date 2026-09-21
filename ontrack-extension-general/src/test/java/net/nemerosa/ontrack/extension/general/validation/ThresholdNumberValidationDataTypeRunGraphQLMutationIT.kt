package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.node.IntNode
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ThresholdNumberValidationDataTypeRunGraphQLMutationIT :
    AbstractValidationDataTypeRunGraphQLMutationTestSupport<ThresholdConfig>() {

    @Autowired
    private lateinit var thresholdNumberValidationDataType: ThresholdNumberValidationDataType

    @Test
    fun `Passed validation with no secret`() {
        testValidation(value = 0, expectedStatus = "PASSED")
    }

    @Test
    fun `Warning validation with a count between the thresholds`() {
        testValidation(value = 3, expectedStatus = "WARNING")
    }

    @Test
    fun `Failed validation with a count above the failure threshold`() {
        testValidation(value = 10, expectedStatus = "FAILED")
    }

    private fun testValidation(
        value: Int,
        expectedStatus: String,
    ) {
        val dataConfig = thresholdNumberValidationDataType.config(
            ThresholdConfig(
                warningThreshold = 0,
                failureThreshold = 5,
                okIfGreater = false
            )
        )
        val dataInput = """
            value: $value
        """.trimIndent()
        val expectedData = IntNode(value)
        //
        testValidationByName(
            dataConfig = dataConfig,
            mutationName = "validateBuildWithNumber",
            dataInput = dataInput,
            expectedData = expectedData,
            expectedStatus = expectedStatus
        )
        //
        testValidationById(
            dataConfig = dataConfig,
            mutationName = "validateBuildByIdWithNumber",
            dataInput = dataInput,
            expectedData = expectedData,
            expectedStatus = expectedStatus
        )
    }

}
