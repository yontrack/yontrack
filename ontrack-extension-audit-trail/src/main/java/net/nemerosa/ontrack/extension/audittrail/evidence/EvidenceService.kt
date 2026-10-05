package net.nemerosa.ontrack.extension.audittrail.evidence

import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ValidationRun

/**
 * The evidences attached to the validation runs.
 *
 * Reading and downloading need the view of the project, whatever the licence: evidences stay
 * readable after it lapses. Attaching needs the creation of validation runs on the project, the
 * licence, and the storage. Deleting needs [EvidenceDelete][net.nemerosa.ontrack.extension.audittrail.security.EvidenceDelete]
 * on the project and the licence, but not the storage.
 */
interface EvidenceService {

    /**
     * Checks that the current user may attach an evidence to a validation run — before the
     * content is even read.
     *
     * @param validationRun Validation run
     * @throws org.springframework.security.access.AccessDeniedException Without the creation of
     * validation runs on its project
     * @throws EvidenceException [EvidenceError.NOT_LICENSED] when the licence is off,
     * [EvidenceError.STORAGE_NOT_CONFIGURED] or [EvidenceError.STORAGE_UNREACHABLE] when the
     * storage cannot be used
     */
    fun checkAttach(validationRun: ValidationRun)

    /**
     * Attaches an evidence to a validation run: its content is streamed to the storage while
     * hashed, then the evidence and its `evidence.attached` entry are written in one transaction,
     * and `evidence.attached` is posted.
     *
     * @param validationRun Validation run
     * @param upload Evidence as sent
     * @return Attached evidence
     * @throws EvidenceException When the evidence is refused — see [checkAttach] and
     * [EvidenceBlobStore.stage]
     */
    fun attach(validationRun: ValidationRun, upload: EvidenceUpload): Evidence

    /**
     * Evidences of a validation run, deleted ones included, in the order of their upload.
     *
     * @param validationRun Validation run, which the current user can see
     */
    fun getEvidences(validationRun: ValidationRun): List<Evidence>

    /**
     * Evidences of every validation run of a build — every run of every stamp — deleted ones
     * included, in the order of their upload.
     *
     * @param build Build, which the current user can see
     */
    fun getEvidences(build: Build): List<Evidence>

    /**
     * Evidence by ID, as long as the current user can see its validation run.
     *
     * @param id ID of the evidence
     * @throws EvidenceNotFoundException When there is no such evidence, or when it is deleted
     */
    fun getEvidence(id: Int): Evidence

    /**
     * Opens the content of an evidence, as long as the current user can see its validation run.
     *
     * @param id ID of the evidence
     * @throws EvidenceNotFoundException When there is no such evidence, when it is deleted, or
     * when its content is missing from the storage
     */
    fun download(id: Int): EvidenceDownload

    /**
     * Deletes an evidence: it is kept, marked as deleted, and an `evidence.deleted` entry and its
     * event are written in one transaction; its blob is then removed, unless another evidence
     * which is not deleted references it. When the storage cannot be used, the blob is left to
     * the sweep.
     *
     * @param id ID of the evidence
     * @return Deleted evidence
     * @throws EvidenceNotFoundException When there is no such evidence, or when it is deleted
     * already
     * @throws org.springframework.security.access.AccessDeniedException Without
     * [EvidenceDelete][net.nemerosa.ontrack.extension.audittrail.security.EvidenceDelete] on its
     * project
     * @throws EvidenceException [EvidenceError.NOT_LICENSED] when the licence is off: the
     * deletion could not be written to the trail
     */
    fun delete(id: Int): Evidence
}
