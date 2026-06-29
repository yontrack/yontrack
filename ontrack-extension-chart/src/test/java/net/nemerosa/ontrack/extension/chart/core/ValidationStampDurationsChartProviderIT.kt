package net.nemerosa.ontrack.extension.chart.core

import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Signature
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals

internal class ValidationStampDurationsChartProviderIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var validationStampDurationsChartProvider: ValidationStampDurationsChartProvider

    @Test
    fun `Chart data for the validation stamp durations`() {
        val durations = listOf(1, 2, 3, 5, 8, 13, 21, 34)
        asAdmin {
            project {
                branch {
                    val vs = validationStamp()
                    // Creates several builds and validations with some date interval
                    val now = LocalDateTime.of(2022, 5, 12, 12, 51, 0)
                    val ref = now.minusDays(25)
                    var index = 0
                    repeat(5) { buildNo ->
                        build(name = "build-$buildNo") {
                            repeat(5) { runNo ->
                                validate(vs, description = "run-$runNo", signature = Signature.of(
                                    ref.plusDays(index.toLong()),
                                    "test"
                                ), duration = durations[index % durations.size])
                                index++
                            }
                        }
                    }
                    // Gets the chart data
                    val chart = validationStampDurationsChartProvider.getChart(
                        options = GetChartOptions(ref = now, interval = "1m", period = "1w"),
                        parameters = ValidationStampChartParameters(id = vs.id()),
                    )
                    // Checks the chart data
                    assertEquals(
                        mapOf(
                            "categories" to listOf("Mean", "90th percentile", "Maximum"),
                            "dates" to listOf("2022-04-19", "2022-04-26", "2022-05-03", "2022-05-10", "2022-05-17"),
                            "data" to mapOf(
                                "mean" to listOf(1.5,12.142857142857142,12.285714285714286,7.571428571428571,17.5),
                                "percentile90" to listOf(2.0,34.0,34.0,21.0,34.0),
                                "maximum" to listOf(2.0,34.0,34.0,21.0,34.0)
                            )
                        ).asJson(),
                        chart.asJson()
                    )
                }
            }
        }
    }

}