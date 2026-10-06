package net.nemerosa.ontrack.extension.audittrail.archive

import net.nemerosa.ontrack.extension.audittrail.evidence.Evidence
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceBlobStore
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceDigestInputStream
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceError
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceException
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceService
import net.nemerosa.ontrack.extension.audittrail.export.TrailExport
import net.nemerosa.ontrack.extension.audittrail.export.TrailExportService
import net.nemerosa.ontrack.extension.audittrail.hash.TrailHashFormatV1
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageService
import net.nemerosa.ontrack.extension.audittrail.storage.EvidenceStorageState
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.model.structure.ValidationRun
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Service
class EvidenceArchiveServiceImpl(
    private val structureService: StructureService,
    private val evidenceService: EvidenceService,
    private val trailExportService: TrailExportService,
    private val evidenceStorageService: EvidenceStorageService,
    private val evidenceBlobStore: EvidenceBlobStore,
    private val jsonMapper: JsonMapper,
) : EvidenceArchiveService {

    private val logger = LoggerFactory.getLogger(EvidenceArchiveServiceImpl::class.java)

    override fun archive(build: Build): EvidenceArchive {
        when (evidenceStorageService.status.state) {
            EvidenceStorageState.NOT_CONFIGURED -> throw EvidenceException(
                EvidenceError.STORAGE_NOT_CONFIGURED,
                "No evidence storage is configured: the evidence archive cannot be downloaded."
            )

            EvidenceStorageState.UNREACHABLE -> throw EvidenceException(
                EvidenceError.STORAGE_UNREACHABLE,
                "The evidence storage cannot be reached: the evidence archive cannot be downloaded."
            )

            EvidenceStorageState.OK -> {}
        }
        // Everything but the content is read before anything is written
        val export = trailExportService.export(build)
        val runs = mutableMapOf<Int, ValidationRun>()
        val evidence = evidenceService.getEvidences(build).map { item ->
            item to runs.getOrPut(item.validationRunId) {
                structureService.getValidationRun(ID.of(item.validationRunId))
            }
        }
        return Archive(build, export, evidence)
    }

    private inner class Archive(
        private val build: Build,
        private val export: TrailExport,
        private val evidence: List<Pair<Evidence, ValidationRun>>,
    ) : EvidenceArchive {

        override fun writeTo(output: OutputStream) {
            val zip = ZipOutputStream(output)
            try {
                zip.write(TRAIL, jsonMapper.writeValueAsBytes(export))
                val items = evidence.map { (item, run) -> write(zip, item, run) }
                val manifest = EvidenceArchiveManifest(
                    manifestVersion = EvidenceArchiveManifest.MANIFEST_VERSION,
                    exportedAt = export.exportedAt,
                    build = export.build,
                    evidence = items,
                )
                zip.write(MANIFEST, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest))
                // Completes the ZIP, leaving the output to its owner
                zip.finish()
            } catch (e: Exception) {
                logger.error("[audit-trail] Evidence archive of build ${build.id()} aborted: ${e.message}", e)
                throw EvidenceArchiveAbortedException(build.id(), e)
            }
        }

        /**
         * Writes the file of an evidence, unless it is deleted or missing, hashing it on the way.
         */
        private fun write(zip: ZipOutputStream, evidence: Evidence, run: ValidationRun): EvidenceArchiveItem {
            if (evidence.deletedAt != null) {
                return item(evidence, run, EvidenceArchiveState.DELETED)
            }
            val content = evidenceBlobStore.open(evidence.sha256)
                ?: return item(evidence, run, EvidenceArchiveState.MISSING)
            val path = EvidenceArchiveNames.path(run.validationStamp.name, run.runOrder, evidence.id, evidence.fileName)
            val digest = EvidenceDigestInputStream(content.stream, Long.MAX_VALUE)
            digest.use { stream ->
                zip.putNextEntry(ZipEntry(path))
                stream.transferTo(zip)
                zip.closeEntry()
            }
            val actualSha256 = digest.sha256
            return if (actualSha256 == evidence.sha256) {
                item(evidence, run, EvidenceArchiveState.ACTIVE, path = path)
            } else {
                item(evidence, run, EvidenceArchiveState.ALTERED, path = path, actualSha256 = actualSha256)
            }
        }

        private fun item(
            evidence: Evidence,
            run: ValidationRun,
            state: EvidenceArchiveState,
            path: String? = null,
            actualSha256: String? = null,
        ) = EvidenceArchiveItem(
            id = evidence.id,
            fileName = evidence.fileName,
            mediaType = evidence.mediaType,
            size = evidence.size,
            sha256 = evidence.sha256,
            collectedAt = TrailHashFormatV1.formatTime(evidence.collectedAt),
            collectedBy = evidence.collectedBy,
            source = evidence.source,
            externalDigest = evidence.externalDigest,
            validationRun = EvidenceArchiveValidationRun(
                id = run.id(),
                validationStamp = run.validationStamp.name,
                runOrder = run.runOrder,
            ),
            state = state,
            path = path,
            actualSha256 = actualSha256,
            deletedAt = evidence.deletedAt?.let { TrailHashFormatV1.formatTime(it) },
        )
    }

    private fun ZipOutputStream.write(name: String, content: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(content)
        closeEntry()
    }

    companion object {
        /**
         * Name of the trail export in the archive
         */
        const val TRAIL = "audit-trail.json"

        /**
         * Name of the manifest in the archive
         */
        const val MANIFEST = "manifest.json"
    }
}
