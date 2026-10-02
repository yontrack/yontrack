package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.extension.audittrail.export.TrailExport
import net.nemerosa.ontrack.extension.audittrail.export.TrailExportService
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Download of the JSON export of the trail of a build.
 */
@RestController
@RequestMapping("/rest/extension/audit-trail")
class AuditTrailExportController(
    private val structureService: StructureService,
    private val trailService: TrailService,
    private val trailExportService: TrailExportService,
) {

    /**
     * Export of the trail of a build ([TrailExport]), as a JSON file to download — whatever the
     * licence, as long as the build has a trail.
     *
     * @param buildId ID of the build, which the caller must be allowed to see
     */
    @GetMapping("builds/{buildId}/export")
    fun export(@PathVariable buildId: Int): ResponseEntity<TrailExport> {
        val build = structureService.getBuild(ID.of(buildId))
        if (!trailService.isTrailAvailable(build)) {
            throw AuditTrailNotFoundException(buildId)
        }
        val export = trailExportService.export(build)
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename("audit-trail-${build.project.name}-${build.branch.name}-${build.name}.json")
                    .build()
                    .toString()
            )
            .body(export)
    }
}
