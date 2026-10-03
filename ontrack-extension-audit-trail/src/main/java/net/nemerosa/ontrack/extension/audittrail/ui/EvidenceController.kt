package net.nemerosa.ontrack.extension.audittrail.ui

import jakarta.servlet.http.HttpServletResponse
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceDisposition
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceError
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceException
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceService
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceUpload
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest
import java.nio.charset.StandardCharsets

/**
 * Upload and download of the evidences.
 *
 * Refusals of an evidence answer an [EvidenceErrorMessage], whose `code` is the one of its
 * [EvidenceError].
 */
@RestController
@RequestMapping("/rest/extension/audit-trail")
class EvidenceController(
    private val structureService: StructureService,
    private val evidenceService: EvidenceService,
) {

    /**
     * Attaches an evidence to a validation run: a `multipart/form-data` request whose `file` part
     * is the content, with the optional fields `fileName` and `mediaType` — taking precedence over
     * those of the part — `sourceTool`, `sourceVersion`, `sourceUrl` and `externalDigest`.
     *
     * The parts are read only once the upload is authorized: the multipart requests are resolved
     * lazily (`spring.servlet.multipart.resolve-lazily`).
     *
     * @param validationRunId ID of the validation run
     * @return Attached evidence, with `201 Created`
     */
    @PostMapping("validation-runs/{validationRunId}/evidence", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @PathVariable validationRunId: Int,
        request: MultipartHttpServletRequest,
    ): ResponseEntity<EvidenceView> {
        val validationRun = structureService.getValidationRun(ID.of(validationRunId))
        evidenceService.checkAttach(validationRun)
        val file = readFile(request)
        val evidence = evidenceService.attach(
            validationRun,
            EvidenceUpload(
                fileName = request.getParameter(FIELD_FILE_NAME) ?: file.originalFilename,
                mediaType = request.getParameter(FIELD_MEDIA_TYPE) ?: file.contentType,
                size = file.size,
                content = { file.inputStream },
                sourceTool = request.getParameter(FIELD_SOURCE_TOOL),
                sourceVersion = request.getParameter(FIELD_SOURCE_VERSION),
                sourceUrl = request.getParameter(FIELD_SOURCE_URL),
                externalDigest = request.getParameter(FIELD_EXTERNAL_DIGEST),
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(EvidenceView.of(evidence))
    }

    /**
     * Downloads the content of an evidence, as long as its validation run can be seen — whatever
     * the licence.
     *
     * Served inline only when [EvidenceDisposition] allows it, as an `application/octet-stream`
     * attachment otherwise, always with `X-Content-Type-Options: nosniff` and a
     * `Content-Security-Policy` which forbids any resource — and any script, through its sandbox,
     * for everything but an inline PDF, which browsers refuse to display in a sandbox.
     *
     * @param evidenceId ID of the evidence
     */
    @GetMapping("evidence/{evidenceId}/download")
    fun download(@PathVariable evidenceId: Int, response: HttpServletResponse) {
        val download = evidenceService.download(evidenceId)
        download.stream.use { stream ->
            val disposition = download.disposition
            val contentDisposition = if (disposition.inline) ContentDisposition.inline() else ContentDisposition.attachment()
            response.status = HttpServletResponse.SC_OK
            response.setHeader(HttpHeaders.CONTENT_TYPE, disposition.contentType)
            response.setContentLengthLong(download.size)
            response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                contentDisposition.filename(download.evidence.fileName, StandardCharsets.UTF_8).build().toString()
            )
            response.setHeader(HEADER_CONTENT_TYPE_OPTIONS, "nosniff")
            response.setHeader(HEADER_CSP, contentSecurityPolicy(disposition))
            // Written as is, without any message converter
            stream.transferTo(response.outputStream)
            response.flushBuffer()
        }
    }

    /**
     * Refusal of an evidence, with its code.
     */
    @ExceptionHandler(EvidenceException::class)
    fun onEvidenceException(ex: EvidenceException): ResponseEntity<EvidenceErrorMessage> =
        ResponseEntity.status(ex.error.status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                EvidenceErrorMessage(
                    status = ex.error.status,
                    code = ex.error.code,
                    message = ex.message ?: ex.error.code,
                )
            )

    private fun readFile(request: MultipartHttpServletRequest): MultipartFile {
        val file = try {
            request.getFile(PART_FILE)
        } catch (_: MaxUploadSizeExceededException) {
            throw EvidenceException(EvidenceError.TOO_LARGE, "The evidence is bigger than the instance allows.")
        } catch (ex: MultipartException) {
            throw EvidenceException(EvidenceError.INVALID, "The evidence cannot be read: ${ex.message}")
        }
        return file ?: throw EvidenceException(EvidenceError.INVALID, "The evidence must be sent in the `$PART_FILE` part.")
    }

    companion object {

        const val PART_FILE = "file"
        const val FIELD_FILE_NAME = "fileName"
        const val FIELD_MEDIA_TYPE = "mediaType"
        const val FIELD_SOURCE_TOOL = "sourceTool"
        const val FIELD_SOURCE_VERSION = "sourceVersion"
        const val FIELD_SOURCE_URL = "sourceUrl"
        const val FIELD_EXTERNAL_DIGEST = "externalDigest"

        const val HEADER_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options"
        const val HEADER_CSP = "Content-Security-Policy"

        /**
         * Policy of every download: no resource, no script — the sandbox
         */
        const val CSP_SANDBOX = "default-src 'none'; style-src 'unsafe-inline'; sandbox"

        /**
         * Policy of an inline PDF: no resource, but no sandbox, in which browsers refuse to
         * display a PDF
         */
        const val CSP_PDF = "default-src 'none'; style-src 'unsafe-inline'"

        private fun contentSecurityPolicy(disposition: EvidenceDisposition): String =
            if (disposition.inline && disposition.contentType == MediaType.APPLICATION_PDF_VALUE) CSP_PDF else CSP_SANDBOX
    }
}
