package net.nemerosa.ontrack.kdsl.spec.extension.audittrail

import net.nemerosa.ontrack.kdsl.connector.FileContent
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.ValidationRunEvidenceQuery
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Build
import net.nemerosa.ontrack.kdsl.spec.ValidationRun
import java.time.LocalDateTime

/**
 * Attaches an evidence to this validation run, uploading its content to the storage of the
 * instance. The attachment is written to the trail of the build.
 *
 * Needs the right to create validation runs on the project.
 *
 * @param file File to attach
 * @return The attached evidence
 * @throws EvidenceRefusedException When the evidence is refused — no storage, a storage which
 * cannot be reached, a file too large, a digest which does not match, no licence, an invalid input
 */
fun ValidationRun.attachEvidence(file: EvidenceFile): Evidence = evidenceCall {
    connector.uploadFile(
        path = "/rest/extension/audit-trail/validation-runs/$id/evidence",
        file = FileContent(
            name = "file",
            content = file.content,
            type = file.mediaType,
        ),
        fields = listOfNotNull(
            "fileName" to file.fileName,
            "mediaType" to file.mediaType,
            file.sourceTool?.let { "sourceTool" to it },
            file.sourceVersion?.let { "sourceVersion" to it },
            file.sourceUrl?.let { "sourceUrl" to it },
            file.externalDigest?.let { "externalDigest" to it },
        ).toMap(),
    ).body.asJson().toEvidence(connector)
}

/**
 * Evidence attached to this validation run, deleted ones included, in the order of their upload.
 */
val ValidationRun.evidence: List<Evidence>
    get() = graphqlConnector.query(
        ValidationRunEvidenceQuery(id.toInt())
    )?.validationRuns?.firstOrNull()?.evidence?.map {
        it.evidenceFragment.toEvidence(connector)
    } ?: emptyList()

/**
 * Validates this build and attaches evidence to the created run, in order.
 *
 * The run is created first: when an evidence is refused, the run stays, with the evidence attached
 * before the refused one, and the refusal is thrown — a missing evidence is an audit gap.
 *
 * @param validationStamp Name of the validation stamp
 * @param evidence Files to attach to the run
 * @param status Status of the run, defaults to the stamp's own default when null
 * @param description Description of the run
 * @param dateTime Time of the run, defaults to the moment of the call when null
 * @return The created validation run
 * @throws EvidenceRefusedException When an evidence is refused
 */
fun Build.validateWithEvidence(
    validationStamp: String,
    evidence: List<EvidenceFile>,
    status: String? = null,
    description: String? = null,
    dateTime: LocalDateTime? = null,
): ValidationRun {
    val run = validate(
        validationStamp = validationStamp,
        status = status,
        description = description,
        dateTime = dateTime,
    )
    evidence.forEach { run.attachEvidence(it) }
    return run
}
