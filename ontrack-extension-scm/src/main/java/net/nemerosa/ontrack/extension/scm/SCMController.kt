package net.nemerosa.ontrack.extension.scm

import net.nemerosa.ontrack.extension.scm.model.SCMFileChangeFilter
import net.nemerosa.ontrack.extension.scm.model.SCMFileChangeFilters
import net.nemerosa.ontrack.extension.scm.service.SCMFileChangeFilterService
import net.nemerosa.ontrack.model.Ack
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.apache.commons.lang3.Strings
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("extension/scm")
class SCMController(
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val scmFileChangeFilterService: SCMFileChangeFilterService
) {
    /**
     * Gets the list of change log file filters
     */
    @GetMapping("changeLog/fileFilter/{projectId}")
    fun getChangeLogFileFilters(@PathVariable projectId: ID): ResponseEntity<List<SCMFileChangeFilter>> {
        // Gets the store
        val config = loadStore(projectId)
        // Resources
        return ResponseEntity.ok(
            config.filters.map { f: SCMFileChangeFilter -> f },
        )
    }

    /**
     * Adding a change log file filter
     */
    @PostMapping("changeLog/fileFilter/{projectId}/create")
    fun createChangeLogFileFilter(
        @PathVariable projectId: ID,
        @RequestBody filter: SCMFileChangeFilter
    ): ResponseEntity<SCMFileChangeFilter> {
        securityService.checkProjectFunction(projectId.get(), ProjectConfig::class.java)
        return securityService.asAdmin {
            // Loads the project
            val project = structureService.getProject(projectId)
            // Saves the new filter into the store
            scmFileChangeFilterService.save(project, filter)
            getChangeLogFileFilter(projectId, filter.name)
        }
    }

    /**
     * Updating a change log file filter
     */
    @PutMapping("changeLog/fileFilter/{projectId}/{name}/update")
    fun saveChangeLogFileFilter(
        @PathVariable projectId: ID,
        @PathVariable name: String?,
        @RequestBody filter: SCMFileChangeFilter
    ): ResponseEntity<SCMFileChangeFilter> {
        check(
            Strings.CS.equals(
                name,
                filter.name
            )
        ) { "The name of the filter in the request body must match the one in the URL" }
        return createChangeLogFileFilter(projectId, filter)
    }

    /**
     * Get a change log file filter
     */
    @GetMapping("changeLog/fileFilter/{projectId}/{name}")
    fun getChangeLogFileFilter(
        @PathVariable projectId: ID,
        @PathVariable name: String?
    ): ResponseEntity<SCMFileChangeFilter> {
        val config = loadStore(projectId)
        // Resource
        return config.filters
            .firstOrNull { filter: SCMFileChangeFilter -> name == filter.name }
            ?.let {
                ResponseEntity.ok(it)
            }
            ?: throw SCMFileChangeFilterNotFound(name)
    }

    private fun loadStore(projectId: ID): SCMFileChangeFilters {
        return securityService.asAdmin {
            val project = structureService.getProject(projectId)
            scmFileChangeFilterService.loadSCMFileChangeFilters(project)
        }
    }

    /**
     * Deletes a change log file filter
     */
    @DeleteMapping("changeLog/fileFilter/{projectId}/{name}/delete")
    fun deleteChangeLogFileFilter(@PathVariable projectId: ID, @PathVariable name: String): Ack {
        securityService.checkProjectFunction(projectId.get(), ProjectConfig::class.java)
        securityService.asAdmin {
            scmFileChangeFilterService.delete(
                structureService.getProject(projectId),
                name
            )
        }
        return Ack.OK
    }
}
