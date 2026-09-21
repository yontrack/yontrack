package net.nemerosa.ontrack.extension.indicators.ui

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.structure.ID

interface ProjectIndicatorService {

    fun getProjectCategoryIndicators(projectId: ID, all: Boolean): List<ProjectCategoryIndicators>

    fun updateIndicator(projectId: ID, typeId: String, input: JsonNode): ProjectIndicator

    fun deleteIndicator(projectId: ID, typeId: String): Ack

    fun getPreviousIndicator(projectIndicator: ProjectIndicator): ProjectIndicator

    fun getHistory(projectIndicator: ProjectIndicator, offset: Int, size: Int): ProjectIndicatorHistory

    fun getProjectIndicators(projectId: ID): List<ProjectIndicator>

    fun findProjectIndicatorByType(projectId: ID, typeId: String): ProjectIndicator?

}