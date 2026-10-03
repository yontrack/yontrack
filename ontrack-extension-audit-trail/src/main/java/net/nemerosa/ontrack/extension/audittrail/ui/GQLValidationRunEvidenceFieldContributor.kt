package net.nemerosa.ontrack.extension.audittrail.ui

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.audittrail.evidence.EvidenceService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.ValidationRun
import org.springframework.stereotype.Component

/**
 * `ValidationRun.evidence` — the evidences attached to the validation run.
 *
 * Always in the schema and readable whatever the licence: evidences stay readable after it lapses.
 * They are read from the database only, whether the storage is reachable or not.
 */
@Component
class GQLValidationRunEvidenceFieldContributor(
    private val gqlTypeEvidence: GQLTypeEvidence,
    private val evidenceService: EvidenceService,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition>? =
        if (projectEntityType == ProjectEntityType.VALIDATION_RUN) {
            listOf(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("evidence")
                    .description("Evidences attached to the validation run, deleted ones included, in the order of their upload")
                    .type(listType(gqlTypeEvidence.typeRef))
                    .dataFetcher { env ->
                        val validationRun: ValidationRun = env.getSource()!!
                        evidenceService.getEvidences(validationRun).map { EvidenceView.of(it) }
                    }
                    .build()
            )
        } else {
            null
        }
}
