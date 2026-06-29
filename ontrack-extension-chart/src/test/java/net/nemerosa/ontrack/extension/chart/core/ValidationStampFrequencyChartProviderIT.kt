package net.nemerosa.ontrack.extension.chart.core

import net.nemerosa.ontrack.extension.chart.GetChartOptions
import net.nemerosa.ontrack.graphql.AbstractQLKTITSupport
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.structure.Signature
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import kotlin.test.assertEquals

internal class ValidationStampFrequencyChartProviderIT : AbstractQLKTITSupport() {

    @Autowired
    private lateinit var validationStampFrequencyChartProvider: ValidationStampFrequencyChartProvider

    @Test
    fun `Chart data for the validation stamp frequency`() {
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
                                validate(
                                    vs, description = "run-$runNo", signature = Signature.of(
                                        ref.plusDays(index.toLong()),
                                        "test"
                                    )
                                )
                                index++
                            }
                        }
                    }
                    // Gets the chart data
                    val chart = validationStampFrequencyChartProvider.getChart(
                        options = GetChartOptions(ref = now, interval = "1m", period = "1w"),
                        parameters = ValidationStampChartParameters(id = vs.id()),
                    )
                    // Checks the chart data
                    assertEquals(
                        mapOf(
                            "dates" to listOf("2022-04-19", "2022-04-26", "2022-05-03", "2022-05-10", "2022-05-17"),
                            "data" to listOf(2.0, 7.0, 7.0, 7.0, 2.0)
                        ).asJson(),
                        chart.asJson()
                    )
                }
            }
        }
    }

}