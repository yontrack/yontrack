package net.nemerosa.ontrack.extension.indicators.ui

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/extension/indicators")
class IndicatorController(
    private val projectIndicatorService: ProjectIndicatorService
) {

    /**
     * Gets the list of ALL indicators for a project
     */
    @GetMapping("project/{projectId}")
    fun getAllProjectIndicators(@PathVariable projectId: ID): ResponseEntity<List<ProjectCategoryIndicators>> =
        ResponseEntity.ok(
            projectIndicatorService.getProjectCategoryIndicators(projectId, all = true)
        )

    /**
     * Updates a project indicator
     */
    @PutMapping("project/{projectId}/indicator/{typeId}/update")
    fun updateIndicator(
        @PathVariable projectId: ID,
        @PathVariable typeId: String,
        @RequestBody input: JsonNode
    ) = ResponseEntity.ok(projectIndicatorService.updateIndicator(projectId, typeId, input))

    /**
     * Deletes a project indicator
     */
    @DeleteMapping("project/{projectId}/indicator/{typeId}/delete")
    fun deleteIndicator(
        @PathVariable projectId: ID,
        @PathVariable typeId: String
    ) = ResponseEntity.ok(projectIndicatorService.deleteIndicator(projectId, typeId))


}