package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.audittrail.canonical.CanonicalJson
import net.nemerosa.ontrack.extension.audittrail.events.AuditTrailEvents
import net.nemerosa.ontrack.extension.audittrail.license.AuditTrailLicense
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.payload
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.validationRun
import net.nemerosa.ontrack.extension.audittrail.listener.TrailPayloads.validationStamp
import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
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
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.io.BufferedInputStream
import java.time.temporal.ChronoUnit

/**
 * The content of an evidence is streamed to the storage **outside** of any transaction of this
 * service — a big upload never holds a database connection — and its metadata, its trail entry and
 * its event are written in one transaction afterwards. A blob stored for an evidence whose
 * transaction then fails is referenced by no row: the collection of the blobs deletes it.
 */
@Service
class EvidenceServiceImpl(
    private val structureService: StructureService,
    private val securityService: SecurityService,
    private val auditTrailLicense: AuditTrailLicense,
    private val evidenceStorageService: EvidenceStorageService,
    private val evidenceBlobStore: EvidenceBlobStore,
    private val evidenceRepository: EvidenceRepository,
    private val trailService: TrailService,
    private val eventPostService: EventPostService,
    private val transactionTemplate: TransactionTemplate,
) : EvidenceService {

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
        val blob = upload.content().use { content ->
            evidenceBlobStore.store(content, upload.size, externalDigest)
        }
        // The actor is taken from the security context, as for any entry
        val actor = (securityService.currentActor ?: Actor.system(reason = null)).asJson()
        val signature = securityService.currentSignature
        return transactionTemplate.execute {
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
