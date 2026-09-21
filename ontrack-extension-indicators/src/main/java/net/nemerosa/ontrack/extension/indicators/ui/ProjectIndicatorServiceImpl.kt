package net.nemerosa.ontrack.extension.indicators.ui

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.indicators.model.Indicator
import net.nemerosa.ontrack.extension.indicators.model.IndicatorService
import net.nemerosa.ontrack.extension.indicators.model.IndicatorTypeService
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.Project
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ProjectIndicatorServiceImpl(
        private val structureService: StructureService,
        private val indicatorService: IndicatorService,
        private val indicatorTypeService: IndicatorTypeService
) : ProjectIndicatorService {

    override fun getProjectCategoryIndicators(projectId: ID, all: Boolean): List<ProjectCategoryIndicators> {
        val project = structureService.getProject(projectId)
        val indicators = indicatorService.getProjectIndicators(project, all)
        return indicators.groupBy(
                keySelector = { indicator ->
                    indicator.type.category
                }
        ).map { (category, indicators) ->
            ProjectCategoryIndicators(
                    project = project,
                    category = category,
                    indicators = indicators.map { indicator ->
                        toProjectIndicator(project, indicator)
                    }
            )
        }
    }

    override fun getProjectIndicators(projectId: ID): List<ProjectIndicator> {
        val project = structureService.getProject(projectId)
        val indicators = indicatorService.getProjectIndicators(project, all = true)
        return indicators.map { toProjectIndicator(project, it) }
    }

    override fun findProjectIndicatorByType(projectId: ID, typeId: String): ProjectIndicator? {
        val project = structureService.getProject(projectId)
        val indicator = indicatorService.getProjectIndicator(project, typeId)
        return toProjectIndicator(project, indicator)
    }

    override fun getPreviousIndicator(projectIndicator: ProjectIndicator): ProjectIndicator {
        val type = indicatorTypeService.getTypeById(projectIndicator.type.id)
        return toProjectIndicator(
                projectIndicator.project,
                indicatorService.getPreviousProjectIndicator(projectIndicator.project, type)
        )
    }

    override fun getHistory(projectIndicator: ProjectIndicator, offset: Int, size: Int): ProjectIndicatorHistory {
        // Gets the indicator type
        val type = indicatorTypeService.getTypeById(projectIndicator.type.id)
        // Gets the history of the indicator
        val history = indicatorService.getProjectIndicatorHistory(projectIndicator.project, type, offset, size)
        // Translates it
        return ProjectIndicatorHistory(
                items = history.items.map { indicator ->
                    toProjectIndicator(projectIndicator.project, indicator)
                },
                offset = history.offset,
                total = history.total
        )
    }

    override fun updateIndicator(projectId: ID, typeId: String, input: JsonNode): ProjectIndicator {
        val project = structureService.getProject(projectId)
        val indicator = indicatorService.updateProjectIndicator<Any>(project, typeId, input)
        return toProjectIndicator(project, indicator)
    }

    override fun deleteIndicator(projectId: ID, typeId: String): Ack {
        val project = structureService.getProject(projectId)
        return indicatorService.deleteProjectIndicator(project, typeId)
    }

    private fun toProjectIndicator(project: Project, indicator: Indicator<*>) = ProjectIndicator(
            project = project,
            indicator = indicator
    )
}