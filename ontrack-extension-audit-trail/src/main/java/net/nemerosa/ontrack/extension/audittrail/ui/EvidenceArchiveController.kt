package net.nemerosa.ontrack.extension.audittrail.ui

import jakarta.servlet.http.HttpServletResponse
import net.nemerosa.ontrack.extension.audittrail.archive.EvidenceArchiveAbortedException
import net.nemerosa.ontrack.extension.audittrail.archive.EvidenceArchiveService
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceException
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Download of the evidence archive of a build ([EvidenceArchiveService]).
 */
@RestController
@RequestMapping("/rest/extension/audit-trail")
class EvidenceArchiveController(
    private val structureService: StructureService,
    private val trailService: TrailService,
    private val evidenceArchiveService: EvidenceArchiveService,
) {

    /**
     * Evidence archive of a build, as a ZIP to download — whatever the licence, as long as the
     * build has a trail.
     *
     * Refused with an [EvidenceErrorMessage] before anything is written when the storage cannot be
     * used. The archive is then streamed: a failure while it is written aborts the response — or,
     * when nothing was sent yet, answers an error without the headers of the archive.
     *
     * @param buildId ID of the build, which the caller must be allowed to see
     */
    @GetMapping("builds/{buildId}/evidence-archive")
    fun archive(@PathVariable buildId: Int, response: HttpServletResponse) {
        val build = structureService.getBuild(ID.of(buildId))
        if (!trailService.isTrailAvailable(build)) {
            throw AuditTrailNotFoundException(buildId)
        }
        val archive = evidenceArchiveService.archive(build)
        response.status = HttpServletResponse.SC_OK
        response.contentType = CONTENT_TYPE_ZIP
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("audit-trail-${build.project.name}-${build.branch.name}-${build.name}.zip")
                .build()
                .toString()
        )
        try {
            archive.writeTo(response.outputStream)
        } catch (e: EvidenceArchiveAbortedException) {
            // Not sent yet: the error is answered without the headers of the archive
            if (!response.isCommitted) {
                response.reset()
            }
            throw e
        }
        response.flushBuffer()
    }

    /**
     * Refusal of the archive, with its code.
     */
    @ExceptionHandler(EvidenceException::class)
    fun onEvidenceException(ex: EvidenceException): ResponseEntity<EvidenceErrorMessage> =
        EvidenceErrorMessage.response(ex)

    companion object {
        const val CONTENT_TYPE_ZIP = "application/zip"
    }
}
