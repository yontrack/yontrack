package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.test.assertPresent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThresholdNumberValidationDataTypeGraphQLMutationIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var thresholdNumberValidationDataType: ThresholdNumberValidationDataType

    @Test
    fun `Creation of a number validation stamp`() {
        asAdmin {
            project {
                branch {
                    run("""
                        mutation {
                            setupNumberValidationStamp(input: {
                                project: "${project.name}",
                                branch: "$name",
                                validation: "test",
                                failureThreshold: 0,
                                okIfGreater: false
                            }) {
                                validationStamp {
                                    id
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """).let { data ->
                        val node = assertNoUserError(data, "setupNumberValidationStamp")
                        assertTrue(node.path("validationStamp").path("id").asInt() != 0, "VS created")

                        assertPresent(structureService.findValidationStampByName(project.name, name, "test")) {
                            assertEquals("test", it.name)
                            assertEquals(
                                ThresholdNumberValidationDataType::class.qualifiedName,
                                it.dataType?.descriptor?.id
                            )
                            assertEquals(
                                ThresholdConfig(
                                    warningThreshold = null,
                                    failureThreshold = 0,
                                    okIfGreater = false
                                ),
                                it.dataType?.config
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Update of a number validation stamp`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp(
                        validationDataTypeConfig = thresholdNumberValidationDataType.config(
                            ThresholdConfig(
                                warningThreshold = 0,
                                failureThreshold = 50,
                                okIfGreater = false
                            )
                        )
                    )
                    run("""
                        mutation {
                            setupNumberValidationStamp(input: {
                                project: "${project.name}",
                                branch: "$name",
                                validation: "${vs.name}",
                                warningThreshold: 10,
                                failureThreshold: 20
                            }) {
                                validationStamp {
                                    id
                                }
                                errors {
                                    message
                                }
                            }
                        }
                    """).let { data ->
                        val node = assertNoUserError(data, "setupNumberValidationStamp")
                        assertEquals(vs.id(), node.path("validationStamp").path("id").asInt(), "VS updated")

                        assertPresent(structureService.findValidationStampByName(project.name, name, vs.name)) {
                            assertEquals(
                                ThresholdNumberValidationDataType::class.qualifiedName,
                                it.dataType?.descriptor?.id
                            )
                            assertEquals(
                                ThresholdConfig(
                                    warningThreshold = 10,
                                    failureThreshold = 20,
                                    okIfGreater = true
                                ),
                                it.dataType?.config
                            )
                        }
                    }
                }
            }
        }
    }
}
