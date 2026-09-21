package net.nemerosa.ontrack.extension.chart

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.asJson
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class DefaultChartService(
    private val chartRegistry: ChartRegistry,
) : ChartService {

    override fun getChart(input: GetChartInput): JsonNode {
        val provider = chartRegistry.getProvider<Any,Any,Chart>(input.name)
        return getChart(provider, input)
    }

    private fun <S: Any, T : Any, C: Chart> getChart(provider: ChartProvider<S, T, C>, input: GetChartInput): JsonNode {
        // Parsing of parameters
        val parameters = provider.parseParameters(input.parameters)
        // Getting the chart
        return provider.getChart(input.options, parameters).asJson()
    }

}