package net.nemerosa.ontrack.extension.scorecard

import net.nemerosa.ontrack.extension.chart.ChartExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtensionFeature
import net.nemerosa.ontrack.model.extension.ExtensionFeatureOptions
import org.springframework.stereotype.Component

@Component
class ScorecardExtensionFeature(
    chartExtensionFeature: ChartExtensionFeature,
) : AbstractExtensionFeature(
    id = "scorecard",
    name = "Delivery scorecard",
    description = "Readings of the projects, computed daily from Yontrack's own data.",
    options = ExtensionFeatureOptions.DEFAULT
        .withDependency(chartExtensionFeature)
)
