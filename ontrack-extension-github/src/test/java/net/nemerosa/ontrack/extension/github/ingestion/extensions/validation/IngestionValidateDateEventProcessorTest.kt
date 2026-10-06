package net.nemerosa.ontrack.extension.github.ingestion.extensions.validation

import io.mockk.mockk
import net.nemerosa.ontrack.extension.api.support.TestNumberValidationDataType
import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class IngestionValidateDateEventProcessorTest {

    @Test
    fun `Payload source for the ingestion of a validate data event with run id`() {
        val processor = mockProcessor()

        assertEquals(
            "validation@run=12345",
            processor.getPayloadSource(
                GitHubIngestionValidateDataPayload(
                    "owner",
                    "repository",
                    "validation",
                    GitHubIngestionValidationDataInput(
                        type = TestNumberValidationDataType::class.java.name,
                        data = mapOf("value" to 50).asJson(),
                    ),
                    validationStatus = "PASSED",
                    runId = 12345,
                )
            )
        )
    }

    @Test
    fun `Payload source for the ingestion of a validate data event with build name`() {
        val processor = mockProcessor()

        assertEquals(
            "validation@name=name",
            processor.getPayloadSource(
                GitHubIngestionValidateDataPayload(
                    "owner",
                    "repository",
                    "validation",
                    GitHubIngestionValidationDataInput(
                        type = TestNumberValidationDataType::class.java.name,
                        data = mapOf("value" to 50).asJson(),
                    ),
                    validationStatus = "PASSED",
                    buildName = "name"
                )
            )
        )
    }

    @Test
    fun `Payload source for the ingestion of a validate data event with build label`() {
        val processor = mockProcessor()

        assertEquals(
            "validation@label=1.0.0",
            processor.getPayloadSource(
                GitHubIngestionValidateDataPayload(
                    "owner",
                    "repository",
                    "validation",
                    GitHubIngestionValidationDataInput(
                        type = TestNumberValidationDataType::class.java.name,
                        data = mapOf("value" to 50).asJson(),
                    ),
                    validationStatus = "PASSED",
                    buildLabel = "1.0.0"
                )
            )
        )
    }

    private fun mockProcessor() = IngestionValidateDateEventProcessor(
        ingestionModelAccessService = mockk(),
        structureService = mockk(),
        runInfoService = mockk(),
        validationRunStatusService = mockk(),
        validationRunService = mockk(),
        validationDataTypeService = mockk(),
    )

}