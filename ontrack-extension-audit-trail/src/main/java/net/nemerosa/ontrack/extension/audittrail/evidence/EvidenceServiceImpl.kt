package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJson
import net.nemerosa.ontrack.extension.audittrail.events.AuditTrailEvents
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.payload
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.validationRun
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.validationStamp
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.extension.audittrail.security.EvidenceDelete
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageState
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseAsJson
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.security.Actor
import net.nemerosa.ontrack.model.security.ProjectView
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.security.ValidationRunCreate
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRun
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.io.BufferedInputStream
import java.time.temporal.ChronoUnit

/**
 * The content of an evidence is streamed to the storage **outside** of any transaction of this
 * service — a big upload never holds a database connection. It is then copied to its blob, and its
 * metadata, its trail entry and its event are written, in one transaction holding the lock of the
 * blob: the [collection of the blobs][EvidenceBlobCollector] never removes a blob between its copy
 * and the commit of the evidence referencing it. A blob copied for an evidence whose transaction
 * then fails is referenced by no row: the sweep removes it.
 *
 * A deletion keeps the evidence, marked as deleted, writes `evidence.deleted` and its event in one
 * transaction, then removes its blob unless another evidence references it — leaving it to the
 * sweep when the storage cannot be used.
 */
@Service
class EvidenceServiceImpl(
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val auditTrailLicense: AuditTrailLicense,
    private val evidenceStorageService: EvidenceStorageService,
    private val evidenceBlobStore: EvidenceBlobStore,
    private val evidenceRepository: EvidenceRepository,
    private val evidenceBlobCollector: EvidenceBlobCollector,
    private val trailService: TrailService,
    private val eventPostService: EventPostService,
    private val transactionTemplate: TransactionTemplate,
) : EvidenceService {

    private val logger = LoggerFactory.getLogger(EvidenceServiceImpl::class.java)

    override fun checkAttach(validationRun: ValidationRun) {
        securityService.checkProjectFunction(validationRun, ValidationRunCreate::class.java)
        if (!auditTrailLicense.auditTrailEnabled) {
            throw EvidenceException(
                EvidenceError.NOT_LICENSED,
                "The licence does not allow the audit trail: no evidence can be attached."
            )
        }
        when (evidenceStorageService.status.state) {
            EvidenceStorageState.NOT_CONFIGURED -> throw EvidenceException(
                EvidenceError.STORAGE_NOT_CONFIGURED,
                "Audit trail is enabled but no evidence storage is configured: evidence cannot be attached."
            )

            EvidenceStorageState.UNREACHABLE -> throw EvidenceException(
                EvidenceError.STORAGE_UNREACHABLE,
                "The evidence storage cannot be reached: evidence cannot be attached."
            )

            EvidenceStorageState.OK -> {}
        }
    }

    override fun attach(validationRun: ValidationRun, upload: EvidenceUpload): Evidence {
        checkAttach(validationRun)
        // Everything the client sends is checked before the content is read
        val fileName = EvidenceInput.fileName(upload.fileName)
        val mediaType = EvidenceInput.mediaType(upload.mediaType)
        val source = EvidenceInput.source(upload.sourceTool, upload.sourceVersion, upload.sourceUrl)
        val externalDigest = EvidenceInput.externalDigest(upload.externalDigest)
        if (upload.size < 0) {
            throw EvidenceException(EvidenceError.INVALID, "The size of the evidence is unknown.")
        }
        val staged = upload.content().use { content ->
            evidenceBlobStore.stage(content, upload.size, externalDigest)
        }
        try {
            return attach(validationRun, staged, fileName, mediaType, source, externalDigest)
        } finally {
            evidenceBlobStore.discard(staged)
        }
    }

    private fun attach(
        validationRun: ValidationRun,
        staged: EvidenceStagedBlob,
        fileName: String,
        mediaType: String,
        source: EvidenceSource?,
        externalDigest: String?,
    ): Evidence {
        // The actor is taken from the security context, as for any entry
        val actor = (securityService.currentActor ?: Actor.system(reason = null)).asJson()
        val signature = securityService.currentSignature
        return transactionTemplate.execute {
            evidenceRepository.lockBlob(staged.sha256)
            val blob = evidenceBlobStore.persist(staged)
            val evidence = evidenceRepository.insert(
                Evidence(
                    id = 0,
                    validationRunId = validationRun.id(),
                    fileName = fileName,
                    mediaType = mediaType,
                    size = blob.size,
                    sha256 = blob.sha256,
                    collectedAt = Time.now.truncatedTo(ChronoUnit.MILLIS),
                    collectedBy = CanonicalJson.canonicalize(actor).parseAsJson(),
                    source = source,
                    externalDigest = externalDigest,
                    deletedAt = null,
                ),
                canonicalCollectedBy = CanonicalJson.canonicalize(actor),
            )
            trailService.append(
                build = validationRun.build,
                type = TrailEntryTypes.EVIDENCE_ATTACHED,
                payload = payload(
                    "validationStamp" to validationStamp(validationRun.validationStamp),
                    "validationRun" to validationRun(validationRun),
                    "evidence" to mapOf(
                        "id" to evidence.id,
                        "fileName" to evidence.fileName,
                        "mediaType" to evidence.mediaType,
                        "size" to evidence.size,
                        "sha256" to evidence.sha256,
                        "source" to source?.let {
                            mapOf("tool" to it.tool, "version" to it.version, "url" to it.url)
                        },
                        "externalDigest" to evidence.externalDigest,
                    ),
                ),
                actor = actor,
            )
            eventPostService.post(AuditTrailEvents.evidenceAttached(validationRun, evidence, signature))
            evidence
        }
    }

    override fun delete(id: Int): Evidence {
        val existing = evidenceRepository.findById(id)?.takeIf { it.deletedAt == null }
            ?: throw EvidenceNotFoundException("Evidence $id cannot be found.")
        // Checks the view of the project
        val validationRun = structureService.getValidationRun(ID.of(existing.validationRunId))
        securityService.checkProjectFunction(validationRun, EvidenceDelete::class.java)
        if (!auditTrailLicense.auditTrailEnabled) {
            // The deletion would be missing from the trail
            throw EvidenceException(
                EvidenceError.NOT_LICENSED,
                "The licence does not allow the audit trail: no evidence can be deleted."
            )
        }
        val actor = (securityService.currentActor ?: Actor.system(reason = null)).asJson()
        val signature = securityService.currentSignature
        val deleted = transactionTemplate.execute {
            val deletedAt = Time.now.truncatedTo(ChronoUnit.MILLIS)
            if (!evidenceRepository.markDeleted(id, deletedAt)) {
                // Deleted concurrently
                throw EvidenceNotFoundException("Evidence $id cannot be found.")
            }
            val evidence = existing.copy(deletedAt = deletedAt)
            trailService.append(
                build = validationRun.build,
                type = TrailEntryTypes.EVIDENCE_DELETED,
                payload = payload(
                    "validationStamp" to validationStamp(validationRun.validationStamp),
                    "validationRun" to validationRun(validationRun),
                    "evidence" to mapOf(
                        "id" to evidence.id,
                        "fileName" to evidence.fileName,
                        "sha256" to evidence.sha256,
                    ),
                ),
                actor = actor,
            )
            eventPostService.post(AuditTrailEvents.evidenceDeleted(validationRun, evidence, signature))
            evidence
        }
        // The blob, once the deletion is committed
        try {
            evidenceBlobCollector.collect(deleted.sha256)
        } catch (e: EvidenceException) {
            logger.warn("[audit-trail] The blob of evidence $id is left to the sweep: ${e.message}")
        }
        return deleted
    }

    override fun getEvidences(validationRun: ValidationRun): List<Evidence> {
        securityService.checkProjectFunction(validationRun, ProjectView::class.java)
        return evidenceRepository.findByValidationRun(validationRun.id())
    }

    override fun getEvidence(id: Int): Evidence {
        val evidence = evidenceRepository.findById(id)?.takeIf { it.deletedAt == null }
            ?: throw EvidenceNotFoundException("Evidence $id cannot be found.")
        // Checks the view of the project
        structureService.getValidationRun(ID.of(evidence.validationRunId))
        return evidence
    }

    override fun download(id: Int): EvidenceDownload {
        val evidence = getEvidence(id)
        val content = evidenceBlobStore.open(evidence.sha256)
            ?: throw EvidenceNotFoundException("The content of evidence $id is missing from the evidence storage.")
        val stream = BufferedInputStream(content.stream, EvidenceDisposition.HEAD_SIZE * 2)
        val head = try {
            stream.mark(EvidenceDisposition.HEAD_SIZE)
            val head = stream.readNBytes(EvidenceDisposition.HEAD_SIZE)
            stream.reset()
            head
        } catch (e: Exception) {
            stream.close()
            throw e
        }
        return EvidenceDownload(
            evidence = evidence,
            disposition = EvidenceDisposition.of(evidence.mediaType, head),
            stream = stream,
            size = content.size,
        )
    }
}
