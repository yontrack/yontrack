package net.nemerosa.ontrack.extension.findings.validation

import net.nemerosa.ontrack.extension.findings.FindingsExtensionFeature
import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.extension.general.validation.CHML
import net.nemerosa.ontrack.extension.general.validation.CHMLLevel
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType
import net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataTypeConfig
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FindingsValidationDataTypeTest {

    private val dataType = FindingsValidationDataType(
        FindingsExtensionFeature(),
        CHMLValidationDataType(GeneralExtensionFeature()),
    )

    private val config = CHMLValidationDataTypeConfig(
        warningLevel = CHMLLevel(CHML.HIGH, 1),
        failedLevel = CHMLLevel(CHML.CRITICAL, 1),
    )

    @Test
    fun `Passed when no threshold is reached`() {
        assertEquals(ValidationRunStatusID.PASSED, status(data(medium = 3, low = 10)))
    }

    @Test
    fun `Warning as CHML`() {
        assertEquals(ValidationRunStatusID.WARNING, status(data(high = 1)))
    }

    @Test
    fun `Failed as CHML`() {
        assertEquals(ValidationRunStatusID.FAILED, status(data(critical = 1, high = 1)))
    }

    @Test
    fun `Accepted findings never trip a threshold`() {
        assertEquals(ValidationRunStatusID.PASSED, status(data(accepted = 12)))
    }

    @Test
    fun `UNKNOWN findings never trip a threshold`() {
        assertEquals(ValidationRunStatusID.PASSED, status(data(unknown = 12)))
    }

    @Test
    fun `No status without configuration, as CHML`() {
        assertNull(dataType.computeStatus(null, data(critical = 1)))
    }

    @Test
    fun `Run data holds the counts under the keys of CHML plus unknown and accepted`() {
        val data = data(critical = 1, high = 2, medium = 3, low = 4, unknown = 5, accepted = 6)
        val json = dataType.toJson(data)
        assertEquals(
            mapOf(
                "levels" to mapOf(
                    "CRITICAL" to 1,
                    "HIGH" to 2,
                    "MEDIUM" to 3,
                    "LOW" to 4,
                ),
                "unknown" to 5,
                "accepted" to 6,
            ).asJson(),
            json
        )
        assertEquals(data, dataType.fromJson(json))
    }

    @Test
    fun `Metrics are the ones of CHML`() {
        assertEquals(
            mapOf(
                "critical" to 1.0,
                "high" to 2.0,
                "medium" to 3.0,
                "low" to 4.0,
            ),
            dataType.getNumericMetrics(data(critical = 1, high = 2, medium = 3, low = 4, unknown = 5, accepted = 6))
        )
        assertEquals(listOf("critical", "high", "medium", "low"), dataType.getMetricNames())
    }

    @Test
    fun `Configuration is the one of CHML`() {
        val json = dataType.configToJson(config)
        assertEquals(config, dataType.configFromJson(json))
        assertEquals(
            config,
            dataType.fromConfigForm(
                mapOf(
                    "warningLevel" to "HIGH",
                    "warningValue" to 1,
                    "failedLevel" to "CRITICAL",
                    "failedValue" to 1,
                ).asJson()
            )
        )
    }

    @Test
    fun `No data from a form`() {
        assertNull(dataType.fromForm(mapOf("CRITICAL" to 1).asJson()))
    }

    private fun status(data: FindingsValidationDataTypeData) =
        dataType.computeStatus(config, data)?.id

    private fun data(
        critical: Int = 0,
        high: Int = 0,
        medium: Int = 0,
        low: Int = 0,
        unknown: Int = 0,
        accepted: Int = 0,
    ) = FindingsValidationDataTypeData(
        levels = mapOf(
            CHML.CRITICAL to critical,
            CHML.HIGH to high,
            CHML.MEDIUM to medium,
            CHML.LOW to low,
        ),
        unknown = unknown,
        accepted = accepted,
    )
}
