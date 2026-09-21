package net.nemerosa.ontrack.extension.indicators.ui

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.indicators.model.Indicator
import net.nemerosa.ontrack.extension.indicators.model.IndicatorCompliance
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.Signature

data class ProjectIndicator(
        val project: Project,
        val type: ProjectIndicatorType,
        val value: JsonNode,
        val compliance: IndicatorCompliance?,
        val comment: String?,
        val signature: Signature
) {

    constructor(project: Project, indicator: Indicator<*>) : this(
            project = project,
            type = ProjectIndicatorType(indicator.type),
            value = indicator.toClientJson(),
            compliance = indicator.compliance,
            comment = indicator.comment,
            signature = indicator.signature
    )
    
}