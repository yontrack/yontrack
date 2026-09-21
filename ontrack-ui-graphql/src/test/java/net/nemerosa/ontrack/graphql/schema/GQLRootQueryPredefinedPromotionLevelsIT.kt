package net.nemerosa.ontrack.graphql.schema

import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GQLRootQueryPredefinedPromotionLevelsIT : AbstractQLKTITSupport() {

    @Test
    fun `All predefined promotion levels`() {
        asAdmin {
            deleteAllPredefinedPromotionLevels()
            val ppls = (1..10).map {
                predefinedPromotionLevel(
                    name = "PL$it"
                )
            }
            run(
                """
                {
                    predefinedPromotionLevels {
                        id
                        name
                    }
                }
            """
            ) { data ->
                assertEquals(
                    data.path("predefinedPromotionLevels").values().map {
                        it.path("id").asInt() to it.path("name").asText()
                    },
                    ppls.map {
                        it.id() to it.name
                    }
                )
            }
        }
    }

    @Test
    fun `All predefined promotion levels when filtering with an empty name`() {
        asAdmin {
            deleteAllPredefinedPromotionLevels()
            val ppls = (1..10).map {
                predefinedPromotionLevel(
                    name = "PL$it"
                )
            }
            run(
                """
                {
                    predefinedPromotionLevels(name: "") {
                        id
                        name
                    }
                }
            """
            ) { data ->
                assertEquals(
                    data.path("predefinedPromotionLevels").values().map {
                        it.path("id").asInt() to it.path("name").asText()
                    },
                    ppls.map {
                        it.id() to it.name
                    }
                )
            }
        }
    }

    @Test
    fun `Predefined promotion levels filtered by name`() {
        asAdmin {
            deleteAllPredefinedPromotionLevels()
            val ppls = (1..10).map {
                predefinedPromotionLevel(
                    name = "PL$it"
                )
            }
            run(
                """
                {
                    predefinedPromotionLevels(name: "PL1") {
                        id
                        name
                    }
                }
            """
            ) { data ->
                assertEquals(
                    data.path("predefinedPromotionLevels").values().map {
                        it.path("id").asInt() to it.path("name").asText()
                    },
                    listOf(
                        ppls[0].run { id() to name },
                        ppls[9].run { id() to name },
                    )
                )
            }
        }
    }

    private fun deleteAllPredefinedPromotionLevels() {
        predefinedPromotionLevelService.predefinedPromotionLevels.forEach {
            predefinedPromotionLevelService.deletePredefinedPromotionLevel(it.id)
        }
    }

}