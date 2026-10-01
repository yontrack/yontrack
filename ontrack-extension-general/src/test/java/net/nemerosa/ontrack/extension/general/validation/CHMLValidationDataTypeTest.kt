package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.json.toJson
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CHMLValidationDataTypeTest {

    private val dataType = CHMLValidationDataType(GeneralExtensionFeature())

    private val config = CHMLValidationDataTypeConfig(
            CHMLLevel(CHML.HIGH, 1),
            CHMLLevel(CHML.CRITICAL, 1)
    )

    @Test
    fun `Status passed`() {
        assertEquals(
                ValidationRunStatusID.PASSED,
                dataType.computeStatus(
                        config,
                        CHMLValidationDataTypeData(
                                mapOf(
                                        CHML.MEDIUM to 1
                                )
                        )
                )?.id
        )
    }

    @Test
    fun `Status warning`() {
        assertEquals(
                ValidationRunStatusID.WARNING,
                dataType.computeStatus(
                        config,
                        CHMLValidationDataTypeData(
                                mapOf(
                                        CHML.HIGH to 1,
                                        CHML.MEDIUM to 1
                                )
                        )
                )?.id
        )
    }

    @Test
    fun `Status failed`() {
        assertEquals(
                ValidationRunStatusID.FAILED,
                dataType.computeStatus(
                        config,
                        CHMLValidationDataTypeData(
                                mapOf(
                                        CHML.CRITICAL to 1,
                                        CHML.HIGH to 1,
                                        CHML.MEDIUM to 1
                                )
                        )
                )?.id
        )
    }

    @Test
    fun `Parsing config from client`() {
        val config = dataType.fromConfigForm(
                mapOf(
                        "failedLevel" to "CRITICAL",
                        "failedValue" to 1,
                        "warningLevel" to "HIGH",
                        "warningValue" to 2
                ).toJson()
        )
        assertNotNull(config) {
            assertEquals(CHML.CRITICAL, it.failedLevel.level)
            assertEquals(1, it.failedLevel.value)
            assertEquals(CHML.HIGH, it.warningLevel.level)
            assertEquals(2, it.warningLevel.value)
        }
    }

    @Test
    fun `Parsing data from client`() {
        val data = dataType.fromForm(mapOf(
                "CRITICAL" to 1,
                "HIGH" to 2,
                "MEDIUM" to 4,
                "LOW" to 8
        ).toJson())
        assertNotNull(data) {
            assertEquals(1, it.levels[CHML.CRITICAL])
            assertEquals(2, it.levels[CHML.HIGH])
            assertEquals(4, it.levels[CHML.MEDIUM])
            assertEquals(8, it.levels[CHML.LOW])
        }
    }

    @Test
    fun `Parsing config from client with the warning tolerance for auto promotion`() {
        val config = dataType.fromConfigForm(
                mapOf(
                        "failedLevel" to "CRITICAL",
                        "failedValue" to 1,
                        "warningLevel" to "HIGH",
                        "warningValue" to 2,
                        "warningPassesAutoPromotion" to true,
                ).toJson()
        )
        assertNotNull(config) {
            assertTrue(it.warningPassesAutoPromotion)
        }
    }

    @Test
    fun `Config to client includes the warning tolerance for auto promotion`() {
        val json = dataType.configToFormJson(config.copy(warningPassesAutoPromotion = true))
        assertNotNull(json) {
            assertTrue(it.path("warningPassesAutoPromotion").asBoolean())
        }
    }

    @Test
    fun `Parsing a stored config without the warning tolerance defaults to false`() {
        val parsed = dataType.configFromJson(
                mapOf(
                        "warningLevel" to mapOf("level" to "HIGH", "value" to 1),
                        "failedLevel" to mapOf("level" to "CRITICAL", "value" to 1),
                ).toJson()
        )
        assertNotNull(parsed) {
            assertFalse(it.warningPassesAutoPromotion)
        }
    }

    private val tolerant = config.copy(warningPassesAutoPromotion = true)

    @Test
    fun `Passed for auto promotion with the flag off`() {
        assertTrue(dataType.isPassedForAutoPromotion(config, ValidationRunStatusID.STATUS_PASSED))
        assertTrue(dataType.isPassedForAutoPromotion(config, ValidationRunStatusID.STATUS_FIXED))
        assertFalse(dataType.isPassedForAutoPromotion(config, ValidationRunStatusID.STATUS_WARNING))
        assertFalse(dataType.isPassedForAutoPromotion(config, ValidationRunStatusID.STATUS_FAILED))
        assertFalse(dataType.isPassedForAutoPromotion(config, ValidationRunStatusID.STATUS_EXPLAINED))
    }

    @Test
    fun `Passed for auto promotion with the flag on`() {
        assertTrue(dataType.isPassedForAutoPromotion(tolerant, ValidationRunStatusID.STATUS_PASSED))
        assertTrue(dataType.isPassedForAutoPromotion(tolerant, ValidationRunStatusID.STATUS_FIXED))
        assertTrue(dataType.isPassedForAutoPromotion(tolerant, ValidationRunStatusID.STATUS_WARNING))
        assertFalse(dataType.isPassedForAutoPromotion(tolerant, ValidationRunStatusID.STATUS_FAILED))
        // Only a last status of exactly WARNING is accepted
        assertFalse(dataType.isPassedForAutoPromotion(tolerant, ValidationRunStatusID.STATUS_EXPLAINED))
        assertFalse(dataType.isPassedForAutoPromotion(tolerant, ValidationRunStatusID.STATUS_INVESTIGATING))
        assertFalse(dataType.isPassedForAutoPromotion(tolerant, ValidationRunStatusID.STATUS_DEFECTIVE))
    }

    @Test
    fun `Passed for auto promotion without any config`() {
        assertTrue(dataType.isPassedForAutoPromotion(null, ValidationRunStatusID.STATUS_PASSED))
        assertFalse(dataType.isPassedForAutoPromotion(null, ValidationRunStatusID.STATUS_WARNING))
        assertFalse(dataType.isPassedForAutoPromotion(null, ValidationRunStatusID.STATUS_FAILED))
    }

}
