package net.nemerosa.ontrack.extension.audittrail.archive

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceSource
import net.nemerosa.ontrack.extension.audittrail.export.TrailExportBuild
import tools.jackson.databind.JsonNode

/**
 * The `manifest.json` of an evidence archive: every evidence of the build, deleted ones included,
 * with what was found of its file while the archive was written — so that each `evidence.attached`
 * entry of the trail has its line, but for the evidence which went with its validation run or its
 * validation stamp: it is gone, and only its `evidence.deleted` entry, with its cascade reason,
 * says so.
 *
 * **Its shape is a compatibility contract**, like the JSON export: a change to it is a new
 * [manifestVersion].
 *
 * ```
 * {
 *   "manifestVersion": 1,
 *   "exportedAt": "2026-10-02T09:00:00.000Z",
 *   "build": {"id": 1042, "project": "payments", "branch": "release-2.4", "name": "2.4.7"},
 *   "evidence": [
 *     {
 *       "id": 17, "fileName": "trivy.json", "mediaType": "application/json", "size": 2048,
 *       "sha256": "9f86d081...", "collectedAt": "2026-10-02T08:20:00.000Z", "collectedBy": {...},
 *       "source": {"tool": "trivy", "version": "0.50.1", "url": "https://ci.example.com/job/1"},
 *       "externalDigest": null,
 *       "validationRun": {"id": 311, "validationStamp": "scan", "runOrder": 1},
 *       "state": "active",
 *       "path": "scan/1/17-trivy.json"
 *     }
 *   ]
 * }
 * ```
 *
 * @property manifestVersion Version of the shape of this document: [MANIFEST_VERSION]
 * @property exportedAt Server time of the archive — the one of the trail export it holds
 * @property build Build of the archive
 * @property evidence Every evidence of the build, deleted ones included, in the order of their upload
 * — but those gone with their validation run
 */
data class EvidenceArchiveManifest(
    val manifestVersion: Int,
    val exportedAt: String,
    val build: TrailExportBuild,
    val evidence: List<EvidenceArchiveItem>,
) {
    companion object {
        /**
         * Version of the shape of the manifests written by this Yontrack
         */
        const val MANIFEST_VERSION = 1
    }
}

/**
 * An evidence in the manifest of an archive.
 *
 * @property id ID of the evidence
 * @property fileName File name of the evidence, as stored — its file in the archive has a safe one
 * @property mediaType Declared media type
 * @property size Size of the content, in bytes, as recorded
 * @property sha256 SHA-256 of the content, as recorded — the one of its `evidence.attached` entry
 * @property collectedAt When the evidence was uploaded, as the times of the trail
 * @property collectedBy Who uploaded it
 * @property source Where the evidence comes from, as its client claimed it
 * @property externalDigest SHA-256 its client claimed, if any
 * @property validationRun Run the evidence is attached to
 * @property state What was found of its file
 * @property path Path of its file in the archive — absent for a [missing][EvidenceArchiveState.MISSING]
 * or a [deleted][EvidenceArchiveState.DELETED] evidence
 * @property actualSha256 SHA-256 of its file, as read — only for an [altered][EvidenceArchiveState.ALTERED]
 * evidence
 * @property deletedAt When it was deleted — only for a [deleted][EvidenceArchiveState.DELETED]
 * evidence; who deleted it is in its `evidence.deleted` entry
 */
data class EvidenceArchiveItem(
    val id: Int,
    val fileName: String,
    val mediaType: String,
    val size: Long,
    val sha256: String,
    val collectedAt: String,
    val collectedBy: JsonNode,
    val source: EvidenceSource?,
    val externalDigest: String?,
    val validationRun: EvidenceArchiveValidationRun,
    val state: EvidenceArchiveState,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val path: String?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val actualSha256: String?,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val deletedAt: String?,
)

/**
 * Validation run of an evidence in the manifest of an archive.
 *
 * @property id ID of the run
 * @property validationStamp Name of its validation stamp
 * @property runOrder Order of the run among the runs of its stamp for the build
 */
data class EvidenceArchiveValidationRun(
    val id: Int,
    val validationStamp: String,
    val runOrder: Int,
)

/**
 * What was found of the file of an evidence while its archive was written.
 */
enum class EvidenceArchiveState(@get:JsonValue val value: String) {

    /**
     * Its file is in the archive, with its recorded SHA-256
     */
    ACTIVE("active"),

    /**
     * Its file is in the archive, as stored, but its SHA-256 is no longer the recorded one
     */
    ALTERED("altered"),

    /**
     * Its file is absent from the evidence storage: it is not in the archive
     */
    MISSING("missing"),

    /**
     * The evidence is deleted: its file is not in the archive
     */
    DELETED("deleted"),
}
