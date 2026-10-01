package net.nemerosa.ontrack.extension.general.validation

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.structure.config
import net.nemerosa.ontrack.test.assertPresent
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CHMLValidationDataTypeGraphQLMutationIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var chmlValidationDataType: CHMLValidationDataType

    @Test
    fun `Creation of a CHML validation stamp`() {
        asAdmin {
            project {
                branch {
                    run("""
                        mutation {
                            setupCHMLValidationStamp(input: {
                                project: "${project.name}",
                                branch: "$name",
                                validation: "test",
                                warningLevel: {level: HIGH, value: 1},
                                failedLevel: {level: CRITICAL, value: 1}
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
                        val node = assertNoUserError(data, "setupCHMLValidationStamp")
                        assertTrue(node.path("validationStamp").path("id").asInt() != 0, "VS created")

                        assertPresent(structureService.findValidationStampByName(project.name, name, "test")) {
                            assertEquals("test", it.name)
                            assertEquals("net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType",
                                it.dataType?.descriptor?.id)
                            assertEquals(
                                CHMLValidationDataTypeConfig(
                                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                                    failedLevel = CHMLLevel(CHML.CRITICAL, 1)
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
    fun `Update of a CHML validation stamp`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp(
                        validationDataTypeConfig = chmlValidationDataType.config(
                            CHMLValidationDataTypeConfig(
                                warningLevel = CHMLLevel(CHML.CRITICAL, 1),
                                failedLevel = CHMLLevel(CHML.CRITICAL, 10)
                            )
                        ))
                    run("""
                        mutation {
                            setupCHMLValidationStamp(input: {
                                project: "${project.name}",
                                branch: "$name",
                                validation: "${vs.name}",
                                warningLevel: {level: HIGH, value: 1},
                                failedLevel: {level: CRITICAL, value: 1}
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
                        val node = assertNoUserError(data, "setupCHMLValidationStamp")
                        assertEquals(vs.id(), node.path("validationStamp").path("id").asInt(), "VS updated")

                        assertPresent(structureService.findValidationStampByName(project.name, name, vs.name)) {
                            assertEquals(vs.name, it.name)
                            assertEquals("net.nemerosa.ontrack.extension.general.validation.CHMLValidationDataType",
                                it.dataType?.descriptor?.id)
                            assertEquals(
                                CHMLValidationDataTypeConfig(
                                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                                    failedLevel = CHMLLevel(CHML.CRITICAL, 1)
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
    fun `Creation of a CHML validation stamp accepting warnings for the auto promotion`() {
        asAdmin {
            project {
                branch {
                    run("""
                        mutation {
                            setupCHMLValidationStamp(input: {
                                project: "${project.name}",
                                branch: "$name",
                                validation: "test",
                                warningLevel: {level: HIGH, value: 1},
                                failedLevel: {level: CRITICAL, value: 1},
                                warningPassesAutoPromotion: true
                            }) {
                                errors {
                                    message
                                }
                            }
                        }
                    """).let { data ->
                        assertNoUserError(data, "setupCHMLValidationStamp")
                        assertPresent(structureService.findValidationStampByName(project.name, name, "test")) {
                            assertEquals(
                                CHMLValidationDataTypeConfig(
                                    warningLevel = CHMLLevel(CHML.HIGH, 1),
                                    failedLevel = CHMLLevel(CHML.CRITICAL, 1),
                                    warningPassesAutoPromotion = true,
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
