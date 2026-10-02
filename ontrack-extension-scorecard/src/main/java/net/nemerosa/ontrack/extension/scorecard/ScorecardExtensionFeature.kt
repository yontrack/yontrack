package net.nemerosa.ontrack.extension.scorecard

import net.nemerosa.ontrack.extension.chart.ChartExtensionFeature
import net.nemerosa.ontrack.extension.environments.EnvironmentsExtensionFeature
import net.nemerosa.ontrack.extension.findings.FindingsExtensionFeature
import net.nemerosa.ontrack.extension.general.GeneralExtensionFeature
import net.nemerosa.ontrack.extension.support.AbstractExtensionFeature
import net.nemerosa.ontrack.model.extension.ExtensionFeatureOptions
import org.springframework.stereotype.Component

@Component
class ScorecardExtensionFeature(
    chartExtensionFeature: ChartExtensionFeature,
    generalExtensionFeature: GeneralExtensionFeature,
    environmentsExtensionFeature: EnvironmentsExtensionFeature,
    findingsExtensionFeature: FindingsExtensionFeature,
) : AbstractExtensionFeature(
    id = "scorecard",
    name = "Delivery scorecard",
    description = "Readings of the projects, computed daily from Yontrack's own data.",
    options = ExtensionFeatureOptions.DEFAULT
        .withDependency(chartExtensionFeature)
        .withDependency(generalExtensionFeature)
        .withDependency(environmentsExtensionFeature)
        .withDependency(findingsExtensionFeature)
)
