package net.nemerosa.ontrack.extension.general.validation

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.RunInfoInput
import net.nemerosa.ontrack.model.structure.ValidationDataTypeConfig
import kotlin.test.assertEquals

abstract class AbstractValidationDataTypeRunGraphQLMutationTestSupport<C> : AbstractQLKTITSupport() {

    protected fun testValidationByName(
        dataConfig: ValidationDataTypeConfig<C>,
        mutationName: String,
        dataInput: String,
        expectedData: JsonNode,
        expectedStatus: String,
        runInfo: RunInfoInput? = null,
    ) {
        testValidation(
            dataConfig = dataConfig,
            mutationName = mutationName,
            buildInput = { build: Build ->
                """
                    project: "${build.project.name}",
                    branch: "${build.branch.name}",
                    build: "${build.name}",
                """.trimIndent()
            },
            dataInput = dataInput,
            expectedData = expectedData,
            expectedStatus = expectedStatus,
            runInfo = runInfo,
        )
    }

    protected fun testValidationById(
        dataConfig: ValidationDataTypeConfig<C>,
        mutationName: String,
        dataInput: String,
        expectedData: JsonNode,
        expectedStatus: String,
        runInfo: RunInfoInput? = null,
    ) {
        testValidation(
            dataConfig = dataConfig,
            mutationName = mutationName,
            buildInput = { build: Build ->
                """
                    id: ${build.id}
                """.trimIndent()
            },
            dataInput = dataInput,
            expectedData = expectedData,
            expectedStatus = expectedStatus,
            runInfo = runInfo,
        )
    }

    private fun testValidation(
        dataConfig: ValidationDataTypeConfig<C>,
        mutationName: String,
        buildInput: (Build) -> String,
        dataInput: String,
        expectedData: JsonNode,
        expectedStatus: String,
        runInfo: RunInfoInput? = null,
    ) {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp(
                        validationDataTypeConfig = dataConfig
                    )
                    build {
                        run(
                            """
                            mutation Mutation(${'$'}runInfo: RunInfoInput) {
                                $mutationName(input: {
                                    ${buildInput(this)},
                                    validation: "${vs.name}",
                                    runInfo: ${'$'}runInfo,
                                    $dataInput
                                }) {
                                    validationRun {
                                        validationStamp {
                                            name
                                        }
                                        data {
                                            descriptor {
                                                id
                                            }
                                            data
                                        }
                                        validationRunStatuses {
                                            statusID {
                                                id
                                            }
                                        }
                                        runInfo {
                                            sourceType
                                            sourceUri
                                            triggerType
                                            runTime
                                        }
                                    }
                                    errors {
                                        message
                                    }
                                }
                            }
                        """, mapOf(
                                "runInfo" to runInfo?.asMap()
                            )
                        ).let { data ->
                            val node = assertNoUserError(data, mutationName)
                            val run = node.path("validationRun")

                            assertEquals(vs.name, run.path("validationStamp").path("name").asText())
                            val runData = run.path("data")
                            assertEquals(
                                expectedData,
                                runData.path("data")
                            )
                            val status = run.path("validationRunStatuses").path(0).path("statusID").path("id").asText()
                            assertEquals(expectedStatus, status)

                            // Run info check
                            if (runInfo != null) {
                                val riNode = run.path("runInfo")
                                assertEquals(runInfo.sourceType, riNode.path("sourceType").asText())
                                assertEquals(runInfo.sourceUri, riNode.path("sourceUri").asText())
                                assertEquals(runInfo.triggerType, riNode.path("triggerType").asText())
                                assertEquals(runInfo.runTime, riNode.path("runTime").asInt())
                            }
                        }
                    }
                }
            }
        }
    }

}