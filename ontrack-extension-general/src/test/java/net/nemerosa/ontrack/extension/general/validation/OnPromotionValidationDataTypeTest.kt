package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.jsonOf
import net.nemerosa.ontrack.model.exceptions.ValidationRunDataInputException
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OnPromotionValidationDataTypeTest {

    private val dataType = OnPromotionValidationDataType(GeneralExtensionFeature())

    private val data = OnPromotionValidationData(
        buildId = 10,
        project = "B",
        branch = "main",
        build = "1.0.0",
        promotion = "SILVER",
        promotionRunId = 20,
    )

    @Test
    fun `Config parsed from JSON`() {
        val config = dataType.configFromJson(
            jsonOf(
                "project" to "B",
                "qualifier" to "prod",
                "promotion" to "SILVER",
            )
        )
        assertEquals(OnPromotionValidationConfig(project = "B", qualifier = "prod", promotion = "SILVER"), config)
    }

    @Test
    fun `Config qualifier defaults to empty`() {
        val config = dataType.configFromJson(
            jsonOf(
                "project" to "B",
                "promotion" to "SILVER",
            )
        )
        assertEquals(OnPromotionValidationConfig(project = "B", qualifier = "", promotion = "SILVER"), config)
    }

    @Test
    fun `Config null from null JSON`() {
        assertNull(dataType.configFromJson(null))
    }

    @Test
    fun `Config round trip`() {
        val config = OnPromotionValidationConfig(project = "B", qualifier = "", promotion = "SILVER")
        assertEquals(config, dataType.configFromJson(dataType.configToJson(config)))
        assertEquals(config, dataType.fromConfigForm(dataType.configToFormJson(config)))
    }

    @Test
    fun `Config with a blank project is refused`() {
        assertFailsWith<ValidationRunDataInputException> {
            dataType.fromConfigForm(jsonOf("project" to " ", "promotion" to "SILVER"))
        }
        assertFailsWith<ValidationRunDataInputException> {
            dataType.configFromJson(jsonOf("project" to "", "promotion" to "SILVER"))
        }
    }

    @Test
    fun `Config with a blank promotion is refused`() {
        assertFailsWith<ValidationRunDataInputException> {
            dataType.fromConfigForm(jsonOf("project" to "B", "promotion" to ""))
        }
        assertFailsWith<ValidationRunDataInputException> {
            dataType.configFromJson(jsonOf("project" to "B"))
        }
    }

    @Test
    fun `Data round trip`() {
        assertEquals(data, dataType.fromJson(dataType.toJson(data)))
        assertEquals(data, dataType.fromJson(data.asJson()))
    }

    @Test
    fun `Data present means passed`() {
        val config = OnPromotionValidationConfig(project = "B", promotion = "SILVER")
        assertEquals(ValidationRunStatusID.STATUS_PASSED, dataType.computeStatus(config, data))
        assertEquals(ValidationRunStatusID.STATUS_PASSED, dataType.computeStatus(null, data))
    }

    @Test
    fun `Data validated when present`() {
        assertEquals(data, dataType.validateData(null, data))
    }

    @Test
    fun `Data from the run form is optional`() {
        assertNull(dataType.fromForm(null))
        assertNull(dataType.fromForm(jsonOf()))
    }

    @Test
    fun `No metrics`() {
        assertNull(dataType.getMetrics(data))
    }
}
