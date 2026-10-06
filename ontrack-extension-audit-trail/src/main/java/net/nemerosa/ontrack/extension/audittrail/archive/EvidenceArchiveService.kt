package net.nemerosa.ontrack.extension.audittrail.archive

import net.nemerosa.ontrack.model.structure.Build
import java.io.OutputStream

/**
 * The evidence archive of a build: one ZIP of its active evidence, the [manifest][EvidenceArchiveManifest]
 * of all its evidence, and the export of its trail.
 *
 * ```
 * audit-trail.json                                    the trail export
 * <validationStamp>/<runOrder>/<evidenceId>-<fileName> one file per active evidence
 * manifest.json                                       written last
 * ```
 *
 * The archive is not signed: its files are vouched for only by the SHA-256s which the signed trail
 * records.
 */
interface EvidenceArchiveService {

    /**
     * Prepares the evidence archive of a build — whatever the licence, as long as the build can
     * be seen — checking everything which can be checked before anything is written.
     *
     * @param build Build, which the current user can see, and which has a trail
     * @return The archive, to write
     * @throws net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceException
     * [STORAGE_NOT_CONFIGURED][net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceError.STORAGE_NOT_CONFIGURED]
     * or [STORAGE_UNREACHABLE][net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceError.STORAGE_UNREACHABLE]
     * when the storage cannot be used
     */
    fun archive(build: Build): EvidenceArchive
}

/**
 * An evidence archive, ready to be written.
 */
interface EvidenceArchive {

    /**
     * Writes the archive as a ZIP, streaming the files of the evidence one by one from the
     * storage, and finishes the ZIP without closing [output].
     *
     * The file of an evidence absent from the storage is left out, and the one of an altered
     * evidence is written as stored: the manifest says which.
     *
     * @param output Where to write the archive
     * @throws EvidenceArchiveAbortedException When the archive cannot be completed — what was
     * written is no valid ZIP
     */
    fun writeTo(output: OutputStream)
}
