package net.nemerosa.ontrack.graphql

import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@AsAdminTest
class BuildValidationsQLIT : AbstractQLKTITSupport() {

    private fun withTestBuild(code: (Build) -> Unit) {
        project {
            branch {
                val vs1 = validationStamp("VS1")
                val vs2 = validationStamp("VS2")
                /* val vs3 = */ validationStamp("VS3")
                build {
                    validate(vs1, ValidationRunStatusID.STATUS_PASSED)
                    validate(vs2, ValidationRunStatusID.STATUS_FAILED)
                    validate(vs2, ValidationRunStatusID.STATUS_PASSED)
                    code(this)
                }
            }
        }
    }

    @Test
    fun `All validations for a build`() {
        withTestBuild { build ->
            run("""{
                builds(id: ${build.id}) {
                   name
                   validations {
                       validationStamp {
                           name
                       }
                       validationRuns {
                           validationRunStatuses {
                               statusID {
                                   id
                               }
                           }
                       }
                   }
                }
            }""") { data ->
                val validations = data.path("builds").first()
                        .path("validations")
                assertEquals(3, validations.size())
                assertEquals(
                        listOf(
                                "VS1", "VS2", "VS3"
                        ),
                        validations.values().map {
                            it.path("validationStamp").path("name").asText()
                        }
                )

                assertEquals(
                        listOf(listOf("PASSED")),
                        validations[0].path("validationRuns")
                                .values().map { runStatus ->
                                    runStatus.path("validationRunStatuses").values().map { status ->
                                        status.path("statusID").path("id").asText()
                                    }
                                }
                )

                assertEquals(
                        listOf(listOf("PASSED"), listOf("FAILED")),
                        validations[1].path("validationRuns")
                                .values().map { runStatus ->
                                    runStatus.path("validationRunStatuses").values().map { status ->
                                        status.path("statusID").path("id").asText()
                                    }
                                }
                )

                assertEquals(
                        emptyList(),
                        validations[2].path("validationRuns")
                                .values().map { runStatus ->
                                    runStatus.path("validationRunStatuses").values().map { status ->
                                        status.path("statusID").path("id").asText()
                                    }
                                }
                )
            }
        }
    }

    @Test
    fun `Validations for a build and a validation stamp`() {
        withTestBuild { build ->
            run("""{
                builds(id: ${build.id}) {
                   name
                   validations(validationStamp: "VS1") {
                       validationStamp {
                           name
                       }
                       validationRuns {
                           validationRunStatuses {
                               statusID {
                                   id
                               }
                           }
                       }
                   }
                }
            }""") { data ->
                val validations = data.path("builds").first()
                        .path("validations")
                assertEquals(1, validations.size())
                assertEquals(
                        listOf(
                                "VS1"
                        ),
                        validations.values().map {
                            it.path("validationStamp").path("name").asText()
                        }
                )

                assertEquals(
                        listOf(listOf("PASSED")),
                        validations[0].path("validationRuns")
                                .values().map { runStatus ->
                                    runStatus.path("validationRunStatuses").values().map { status ->
                                        status.path("statusID").path("id").asText()
                                    }
                                }
                )
            }
        }
    }

    @Test
    fun `Validations for a build with limited count of runs`() {
        withTestBuild { build ->
            run("""{
                builds(id: ${build.id}) {
                   name
                   validations {
                       validationStamp {
                           name
                       }
                       validationRuns(count: 1) {
                           validationRunStatuses {
                               statusID {
                                   id
                               }
                           }
                       }
                   }
                }
            }""") { data ->
                val validations = data.path("builds").first()
                        .path("validations")
                assertEquals(3, validations.size())
                assertEquals(
                        listOf(
                                "VS1", "VS2", "VS3"
                        ),
                        validations.values().map {
                            it.path("validationStamp").path("name").asText()
                        }
                )

                assertEquals(
                        listOf(listOf("PASSED")),
                        validations[0].path("validationRuns")
                                .values().map { runStatus ->
                                    runStatus.path("validationRunStatuses").values().map { status ->
                                        status.path("statusID").path("id").asText()
                                    }
                                }
                )

                assertEquals(
                        listOf(listOf("PASSED")),
                        validations[1].path("validationRuns")
                                .values().map { runStatus ->
                                    runStatus.path("validationRunStatuses").values().map { status ->
                                        status.path("statusID").path("id").asText()
                                    }
                                }
                )

                assertEquals(
                        emptyList(),
                        validations[2].path("validationRuns")
                                .values().map { runStatus ->
                                    runStatus.path("validationRunStatuses").values().map { status ->
                                        status.path("statusID").path("id").asText()
                                    }
                                }
                )
            }
        }
    }

}