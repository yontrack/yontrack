package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.node.IntNode
import net.nemerosa.ontrack.model.structure.config
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ThresholdPercentageValidationDataTypeRunGraphQLMutationIT :
    AbstractValidationDataTypeRunGraphQLMutationTestSupport<ThresholdConfig>() {

    @Autowired
    private lateinit var thresholdPercentageValidationDataType: ThresholdPercentageValidationDataType

    @Test
    fun `Passed validation tests`() {
        testValidation(value = 65, expectedStatus = "PASSED")
    }

    @Test
    fun `Warning validation tests`() {
        testValidation(value = 55, expectedStatus = "WARNING")
    }

    @Test
    fun `Failed validation tests`() {
        testValidation(value = 45, expectedStatus = "FAILED")
    }

    private fun testValidation(
        value: Int,
        expectedStatus: String,
    ) {
        val dataConfig = thresholdPercentageValidationDataType.config(
            ThresholdConfig(
                warningThreshold = 60,
                failureThreshold = 50,
                okIfGreater = true
            )
        )
        val dataInput = """
            value: $value
        """.trimIndent()
        val expectedData = IntNode(value)
        //
        testValidationByName(
            dataConfig = dataConfig,
            mutationName = "validateBuildWithPercentage",
            dataInput = dataInput,
            expectedData = expectedData,
            expectedStatus = expectedStatus
        )
        //
        testValidationById(
            dataConfig = dataConfig,
            mutationName = "validateBuildByIdWithPercentage",
            dataInput = dataInput,
            expectedData = expectedData,
            expectedStatus = expectedStatus
        )
    }

}