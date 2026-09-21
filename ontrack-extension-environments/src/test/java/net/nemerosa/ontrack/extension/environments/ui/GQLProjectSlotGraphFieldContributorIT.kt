package net.nemerosa.ontrack.extension.environments.ui

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.environments.SlotTestSupport
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.asJson
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class GQLProjectSlotGraphFieldContributorIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var slotTestSupport: SlotTestSupport

    @Test
    fun `Project environment graph using order only`() {
        asAdmin {
            val dev = slotTestSupport.slot(order = 10)
            val staging = slotTestSupport.slot(order = 20, project = dev.project)
            val acceptance = slotTestSupport.slot(order = 30, project = dev.project)
            val demo = slotTestSupport.slot(order = 30, project = dev.project)
            val production = slotTestSupport.slot(order = 40, project = dev.project)

            run(
                """
                    {
                        project(id: ${dev.project.id}) {
                            id
                            slotGraph {
                                slotNodes {
                                    slot {
                                        id
                                    }
                                    parents {
                                        id
                                    }
                                }
                            }
                        }
                    }
                """.trimIndent()
            ) { data ->
                assertEquals(
                    mapOf(
                        "project" to mapOf(
                            "id" to dev.project.id().toString(),
                            "slotGraph" to mapOf(
                                "slotNodes" to listOf(
                                    mapOf(
                                        "slot" to mapOf(
                                            "id" to dev.id,
                                        ),
                                        "parents" to emptyList<JsonNode>(),
                                    ),
                                    mapOf(
                                        "slot" to mapOf(
                                            "id" to staging.id,
                                        ),
                                        "parents" to listOf(
                                            mapOf(
                                                "id" to dev.id,
                                            )
                                        ),
                                    ),
                                    mapOf(
                                        "slot" to mapOf(
                                            "id" to acceptance.id,
                                        ),
                                        "parents" to listOf(
                                            mapOf(
                                                "id" to staging.id,
                                            )
                                        ),
                                    ),
                                    mapOf(
                                        "slot" to mapOf(
                                            "id" to demo.id,
                                        ),
                                        "parents" to listOf(
                                            mapOf(
                                                "id" to staging.id,
                                            )
                                        ),
                                    ),
                                    mapOf(
                                        "slot" to mapOf(
                                            "id" to production.id,
                                        ),
                                        "parents" to listOf(
                                            mapOf(
                                                "id" to acceptance.id,
                                            ),
                                            mapOf(
                                                "id" to demo.id,
                                            ),
                                        ),
                                    ),
                                )
                            )
                        )
                    ).asJson(),
                    data
                )
            }
        }
    }

}