package net.nemerosa.ontrack.extension.chart.core

import tools.jackson.databind.node.NullNode
import net.nemerosa.ontrack.extension.chart.ChartDefinition
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@AsAdminTest
internal class GQLValidationStampChartsFieldContributorIT : AbstractQLKTITSupport() {

    @Test
    fun `Default supported charts for a validation stamp`() {
        project {
            branch {
                val vs = validationStamp()
                run(
                    """{
                    validationStamp(id: ${vs.id}) {
                        charts {
                            id
                            title
                            type
                            config
                            parameters
                        }
                    }
                }"""
                ) { data ->
                    val charts = data.path("validationStamp").path("charts").values().map {
                        it.parse<ChartDefinition>()
                    }.associateBy { it.id }
                    assertEquals(3, charts.size)
                    assertEquals(
                        ChartDefinition(
                            id = "validation-stamp-durations",
                            title = "Validation stamp duration",
                            type = "duration",
                            config = NullNode.instance,
                            parameters = mapOf("id" to vs.id()).asJson(),
                        ),
                        charts["validation-stamp-durations"]
                    )
                    assertEquals(
                        ChartDefinition(
                            id = "validation-stamp-frequency",
                            title = "Validation stamp frequency",
                            type = "count",
                            config = NullNode.instance,
                            parameters = mapOf("id" to vs.id()).asJson(),
                        ),
                        charts["validation-stamp-frequency"]
                    )
                    assertEquals(
                        ChartDefinition(
                            id = "validation-stamp-stability",
                            title = "Validation stamp stability",
                            type = "percentage",
                            config = mapOf("name" to "% of success").asJson(),
                            parameters = mapOf("id" to vs.id()).asJson(),
                        ),
                        charts["validation-stamp-stability"]
                    )
                }
            }
        }
    }


}