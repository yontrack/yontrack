package net.nemerosa.ontrack.extension.audittrail.ui

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceService
import net.nemerosa.ontrack.graphql.schema.Mutation
import net.nemerosa.ontrack.graphql.support.TypedMutationProvider
import org.springframework.stereotype.Component

/**
 * Mutations on the evidences — their upload being a multipart REST end point.
 */
@Component
class EvidenceMutations(
    private val evidenceService: EvidenceService,
) : TypedMutationProvider() {

    override val mutations: List<Mutation> = listOf(
        simpleMutation(
            name = "deleteEvidence",
            description = "Deletes an evidence: it is kept, marked as deleted, and its deletion is written to the " +
                    "trail of its build. Requires the EvidenceDelete function on its project.",
            input = DeleteEvidenceInput::class,
            outputName = "evidence",
            outputDescription = "Deleted evidence",
            outputType = EvidenceView::class,
        ) { input ->
            EvidenceView.of(evidenceService.delete(input.id))
        },
    )
}

/**
 * Input of the `deleteEvidence` mutation.
 */
data class DeleteEvidenceInput(
    @APIDescription("ID of the evidence to delete")
    val id: Int,
)
