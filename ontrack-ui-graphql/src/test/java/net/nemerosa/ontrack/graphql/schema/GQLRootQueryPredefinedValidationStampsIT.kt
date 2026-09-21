package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GQLRootQueryPredefinedValidationStampsIT : AbstractQLKTITSupport() {

    @Test
    fun `All predefined validation stamps`() {
        asAdmin {
            deleteAllPredefinedValidationStamps()
            val pvss = (1..10).map {
                predefinedValidationStamp(
                    name = "VS$it"
                )
            }
            run(
                """
                {
                    predefinedValidationStamps {
                        id
                        name
                    }
                }
            """
            ) { data ->
                assertEquals(
                    data.path("predefinedValidationStamps").values().map {
                        it.path("id").asInt() to it.path("name").asText()
                    },
                    pvss.sortedBy { it.name }.map {
                        it.id() to it.name
                    }
                )
            }
        }
    }

    @Test
    fun `All predefined validation stamps when filtering with an empty name`() {
        asAdmin {
            deleteAllPredefinedValidationStamps()
            val pvss = (1..10).map {
                predefinedValidationStamp(
                    name = "VS$it"
                )
            }
            run(
                """
                {
                    predefinedValidationStamps(name: "") {
                        id
                        name
                    }
                }
            """
            ) { data ->
                assertEquals(
                    data.path("predefinedValidationStamps").values().map {
                        it.path("id").asInt() to it.path("name").asText()
                    },
                    pvss.sortedBy { it.name }.map {
                        it.id() to it.name
                    }
                )
            }
        }
    }

    @Test
    fun `Predefined validation stamps filtered by name`() {
        asAdmin {
            deleteAllPredefinedValidationStamps()
            val pvss = (1..10).map {
                predefinedValidationStamp(
                    name = "VS$it"
                )
            }
            run(
                """
                {
                    predefinedValidationStamps(name: "VS1") {
                        id
                        name
                    }
                }
            """
            ) { data ->
                assertEquals(
                    listOf(
                        pvss[0].run { id() to name },
                        pvss[9].run { id() to name },
                    ),
                    data.path("predefinedValidationStamps").values().map {
                        it.path("id").asInt() to it.path("name").asText()
                    },
                )
            }
        }
    }

    private fun deleteAllPredefinedValidationStamps() {
        predefinedValidationStampService.predefinedValidationStamps.forEach {
            predefinedValidationStampService.deletePredefinedValidationStamp(it.id)
        }
    }

}