package net.nemerosa.ontrack.boot.ui

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.api.support.TestNumberValidationDataType
import net.nemerosa.ontrack.extension.api.support.TestValidationDataType
import net.nemerosa.ontrack.json.toJson
import net.nemerosa.ontrack.model.exceptions.*
import net.nemerosa.ontrack.model.structure.ValidationRun
import net.nemerosa.ontrack.model.structure.ValidationRunStatusService
import net.nemerosa.ontrack.model.structure.ValidationStamp
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.test.assertIs
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * All tests with data associated to validation runs.
 *
 * Different axes of test:
 *
 * * validation stamp with or without data type
 * * validation stamp data type with or without computed status
 * * validation run with or without data
 * * validation run status provided or not
 * * validation run data valid or invalid
 */
class ValidationRunControllerIT : AbstractWebTestSupport() {

    @Autowired
    private lateinit var validationRunController: ValidationRunController

    @Autowired
    private lateinit var validationRunStatusService: ValidationRunStatusService

    @Autowired
    private lateinit var testNumberValidationDataType: TestNumberValidationDataType

    @Autowired
    private lateinit var testValidationDataType: TestValidationDataType

    @Test
    fun `Stamp with type, computed status, run with data, provided status, valid data`() {
        doTestVS().withType().withComputedStatus(50)
            .forRun().withData(mapOf("value" to 40)).withStatus("PASSED")
            .execute()
            .mustBe("PASSED").withData(40)
    }

    @Test
    fun `Stamp with type, computed status, run with data, provided status, invalid data`() {
        assertFailsWith<ValidationRunDataFormatException> {
            doTestVS().withType().withComputedStatus(50)
                .forRun().withData("text".toJson()!!).withStatus("PASSED")
                .execute()
        }
    }

    @Test
    fun `Stamp with type, computed status, run with data, unprovided status, valid data`() {
        doTestVS().withType().withComputedStatus(50)
            .forRun().withData(mapOf("value" to 40))
            .execute()
            .mustBe("FAILED").withData(40)
    }

    @Test
    fun `Stamp with type, computed status, run with data, unprovided status, invalid data`() {
        assertFailsWith<ValidationRunDataInputException> {
            doTestVS().withType().withComputedStatus(50)
                .forRun().withData("text".toJson()!!)
                .execute()
        }
    }

    @Test
    fun `Stamp with type, computed status, run without data, provided status`() {
        doTestVS().withType().withComputedStatus(50)
            .forRun().withStatus("PASSED")
            .execute()
            .mustBe("PASSED").withNoData()
    }

    @Test
    fun `Stamp with type, computed status, run without data, unprovided status`() {
        assertFailsWith<ValidationRunDataStatusRequiredBecauseNoDataException> {
            doTestVS().withType().withComputedStatus(50)
                .forRun()
                .execute()
        }
    }

    @Test
    fun `Stamp with type, no computed status, run with data, provided status, valid data`() {
        doTestVS().withType()
            .forRun().withData(mapOf("value" to 40)).withStatus("PASSED")
            .execute()
            .mustBe("PASSED").withData(40)
    }

    @Test
    fun `Stamp with type, no computed status, run with data, provided status, invalid data`() {
        assertFailsWith<ValidationRunDataFormatException> {
            doTestVS().withType()
                .forRun().withData("text".toJson()!!).withStatus("PASSED")
                .execute()
        }
    }

    @Test
    fun `Stamp with type, no computed status, run with data, unprovided status, valid data`() {
        doTestVS().withType()
            .forRun().withData(mapOf("value" to 40))
            .execute()
            .mustBe("PASSED").withData(40)
    }

    @Test
    fun `Stamp with type, no computed status, run with data, unprovided status, invalid data`() {
        assertFailsWith<ValidationRunDataFormatException> {
            doTestVS().withType()
                .forRun().withData("text".toJson()!!)
                .execute()
        }
    }

    @Test
    fun `Stamp with type, no computed status, run without data, provided status`() {
        doTestVS().withType()
            .forRun().withStatus("FAILED")
            .execute()
            .mustBe("FAILED").withNoData()
    }

    @Test
    fun `Stamp with type, no computed status, run without data, unprovided status`() {
        assertFailsWith<ValidationRunDataStatusRequiredBecauseNoDataException> {
            doTestVS().withType()
                .forRun()
                .execute()
        }
    }

    @Test
    fun `Stamp without type, run with data, provided status, valid data`() {
        doTestVS()
            .forRun().withData(mapOf("value" to 40)).withStatus("FAILED")
            .execute()
            .mustBe("FAILED").withData(40)
    }

    @Test
    fun `Stamp without type, run with data, provided status, invalid data`() {
        assertFailsWith<ValidationRunDataFormatException> {
            doTestVS()
                .forRun().withData("text".toJson()!!).withStatus("FAILED")
                .execute()
        }
    }

    @Test
    fun `Stamp without type, run with data, unprovided status, valid data`() {
        assertFailsWith<ValidationRunDataStatusRequiredBecauseNoDataTypeException> {
            doTestVS()
                .forRun().withData("value" to 40)
                .execute()
        }
    }

    @Test
    fun `Stamp without type, run with data, unprovided status, invalid data`() {
        assertFailsWith<ValidationRunDataFormatException> {
            doTestVS()
                .forRun().withData("text".toJson()!!)
                .execute()
        }
    }

    @Test
    fun `Stamp without type, run without data, provided status`() {
        doTestVS()
            .forRun().withStatus("FAILED")
            .execute()
            .mustBe("FAILED").withNoData()
    }

    @Test
    fun `Stamp without type, run without data, unprovided status`() {
        assertFailsWith<ValidationRunDataStatusRequiredBecauseNoDataException> {
            doTestVS()
                .forRun()
                .execute()
        }
    }

    // ---

    @Test
    fun `Stamp without type, run with data, unprovided status, unknown type`() {
        assertFailsWith<ValidationRunDataTypeNotFoundException> {
            doTestVS()
                .forRun().withData("value" to 50).withDataType("unknown")
                .execute()
        }
    }

    // ---

    @Test
    fun `Invalid JSON must be caught as an input exception`() {
        assertFailsWith<ValidationRunDataJSONInputException> {
            project {
                branch {
                    val vs = validationStamp(
                        "VS",
                        testValidationDataType.config(null)
                    )
                    build<ValidationRun>("1.0.0") {
                        // Calling the validation run controller
                        validationRunController.newValidationRun(
                            id,
                            ValidationRunRequestForm(
                                description = "",
                                validationRunStatusId = null,
                                validationStampData = ValidationRunRequestFormData(
                                    id = vs.name,
                                    type = testValidationDataType.descriptor.id,
                                    data = mapOf("CRITICAL" to 1).toJson() // CRITICAL instead of critical
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    // ---

    @Test
    fun `Request type is more important for the parsing than the stamp type`() {
        assertFailsWith<ValidationRunDataMismatchException> {
            project {
                branch {
                    val vs = validationStamp(
                        "VS",
                        testValidationDataType.config(null)
                    )
                    build<ValidationRun>("1.0.0") {
                        // Calling the validation run controller
                        validationRunController.newValidationRun(
                            id,
                            ValidationRunRequestForm(
                                description = "",
                                validationRunStatusId = null,
                                validationStampData = ValidationRunRequestFormData(
                                    id = vs.name,
                                    // Sending a number type where a fraction is expected
                                    type = testNumberValidationDataType.descriptor.id,
                                    data = mapOf("value" to 42).toJson()
                                )
                            )
                        )
                    }
                }
            }
        }
    }

    private fun doTestVS() = VS()

    private inner class VS {

        var typed: Boolean = false
        var threshold: Int? = null

        fun withType() = apply {
            typed = true
        }

        fun withComputedStatus(value: Int) = apply {
            threshold = value
        }

        fun forRun() = VRun(this)

        fun <T> withValidationStamp(fn: (ValidationStamp) -> T): T =
            project<T> {
                branch<T> {
                    val vs = validationStamp(
                        "VS",
                        if (typed) {
                            testNumberValidationDataType.config(threshold)
                        } else {
                            null
                        }
                    )
                    fn(vs)
                }
            }
    }

    private inner class VRun(private val vs: VS) {
        private var data: JsonNode? = null
        private var dataType: String? = null
        private var status: String? = null

        fun withData(value: JsonNode) = apply {
            data = value
        }

        fun withData(map: Map<String, *>) = apply {
            data = map.toJson()!!
        }

        fun withData(pair: Pair<String, *>) = withData(mapOf(pair))

        fun withStatus(s: String) = apply {
            status = s
        }

        fun withDataType(s: String) = apply {
            dataType = s
        }

        fun execute(): VTest {
            val run = vs.withValidationStamp {
                it.branch.build<ValidationRun>("1.0.0") {
                    // Calling the validation run controller
                    validationRunController.newValidationRun(
                        id,
                        ValidationRunRequestForm(
                            description = "",
                            validationRunStatusId = status,
                            validationStampData = ValidationRunRequestFormData(
                                id = it.name,
                                type = dataType
                                    ?: if (vs.typed) null else testNumberValidationDataType.descriptor.id,
                                data = data
                            )
                        )
                    )
                }
            }
            return VTest(run)
        }

    }

    private class VTest(private val run: ValidationRun) {
        fun mustBe(status: String) = apply {
            assertEquals(
                status,
                run.lastStatus.statusID.id
            )
        }

        fun withData(expectedValue: Int) = apply {
            assertNotNull(run.data) {
                assertIs<Int>(it.data) {
                    assertEquals(expectedValue, it)
                }
            }
        }

        fun withNoData() = apply {
            assertNull(run.data)
        }
    }

}