package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.toJson
import net.nemerosa.ontrack.model.structure.NameDescription
import net.nemerosa.ontrack.model.structure.PredefinedValidationStamp
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks the `formConfig` field of the `ValidationDataTypeConfig` GraphQL type, which
 * returns the configuration in the shape expected by the edition forms and mutations.
 */
class ValidationDataTypeFormConfigGraphQLIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var chmlValidationDataType: CHMLValidationDataType

    @Autowired
    private lateinit var thresholdPercentageValidationDataType: ThresholdPercentageValidationDataType

    private val chmlConfig = CHMLValidationDataTypeConfig(
        failedLevel = CHMLLevel(CHML.CRITICAL, 1),
        warningLevel = CHMLLevel(CHML.HIGH, 2),
    )

    private val chmlFormConfig = mapOf(
        "failedLevel" to "CRITICAL",
        "failedValue" to 1,
        "warningLevel" to "HIGH",
        "warningValue" to 2,
    ).asJson()

    @Test
    fun `Form config of a CHML validation stamp is flat while its config stays nested`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp(validationDataTypeConfig = chmlValidationDataType.config(chmlConfig))
                    run(
                        """
                            {
                                validationStamp(id: ${vs.id}) {
                                    dataType {
                                        config
                                        formConfig
                                    }
                                }
                            }
                        """
                    ) { data ->
                        val dataType = data.path("validationStamp").path("dataType")
                        assertEquals(chmlConfig.toJson(), dataType.path("config"))
                        assertEquals(chmlFormConfig, dataType.path("formConfig"))
                    }
                }
            }
        }
    }

    @Test
    fun `Saving a CHML validation stamp with its form config leaves its config unchanged`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp(validationDataTypeConfig = chmlValidationDataType.config(chmlConfig))
                    val formConfig = run(
                        """
                            {
                                validationStamp(id: ${vs.id}) {
                                    dataType {
                                        formConfig
                                    }
                                }
                            }
                        """
                    ).path("validationStamp").path("dataType").path("formConfig")
                    run(
                        """
                            mutation(${'$'}config: JSON) {
                                updateValidationStampById(input: {
                                    id: ${vs.id},
                                    name: "${vs.name}",
                                    description: "",
                                    dataType: "${CHMLValidationDataType::class.java.name}",
                                    dataTypeConfig: ${'$'}config,
                                }) {
                                    errors {
                                        message
                                    }
                                }
                            }
                        """,
                        mapOf("config" to formConfig)
                    ) { data ->
                        assertNoUserError(data, "updateValidationStampById")
                    }
                    assertEquals(
                        chmlConfig,
                        structureService.getValidationStamp(vs.id).dataType?.config
                    )
                }
            }
        }
    }

    @Test
    fun `Form config of a CHML predefined validation stamp is flat`() {
        asAdmin {
            val name = uid("pvs-")
            predefinedValidationStampService.newPredefinedValidationStamp(
                PredefinedValidationStamp.of(NameDescription.nd(name, ""))
                    .withDataType(chmlValidationDataType.config(chmlConfig))
            )
            run(
                """
                    {
                        predefinedValidationStamps(name: "$name") {
                            dataType {
                                formConfig
                            }
                        }
                    }
                """
            ) { data ->
                val pvs = data.path("predefinedValidationStamps").find {
                    it.path("dataType").path("formConfig") == chmlFormConfig
                }
                assertTrue(pvs != null, "CHML form config returned for the predefined validation stamp")
            }
        }
    }

    @Test
    fun `Form config of a threshold percentage validation stamp is the same as its config`() {
        asAdmin {
            project {
                branch {
                    val thresholdConfig = ThresholdConfig(
                        warningThreshold = 60,
                        failureThreshold = 40,
                        okIfGreater = true,
                    )
                    val vs = validationStamp(
                        validationDataTypeConfig = thresholdPercentageValidationDataType.config(thresholdConfig)
                    )
                    run(
                        """
                            {
                                validationStamp(id: ${vs.id}) {
                                    dataType {
                                        config
                                        formConfig
                                    }
                                }
                            }
                        """
                    ) { data ->
                        val dataType = data.path("validationStamp").path("dataType")
                        assertEquals(thresholdConfig.toJson(), dataType.path("formConfig"))
                        assertEquals(dataType.path("config"), dataType.path("formConfig"))
                    }
                }
            }
        }
    }

    @Test
    fun `Form config of a validation stamp without data type is null`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    run(
                        """
                            {
                                validationStamp(id: ${vs.id}) {
                                    dataType {
                                        formConfig
                                    }
                                }
                            }
                        """
                    ) { data ->
                        assertTrue(data.path("validationStamp").path("dataType").isNull)
                    }
                }
            }
        }
    }
}
