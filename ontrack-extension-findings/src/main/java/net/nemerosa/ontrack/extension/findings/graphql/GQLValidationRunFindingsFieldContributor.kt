package net.nemerosa.ontrack.extension.findings.graphql

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.findings.query.FindingQueryService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.support.listType
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.ValidationRun
import org.springframework.stereotype.Component

/**
 * `ValidationRun.findings`: the security findings reported by a scan, with their observation by
 * this scan.
 */
@Component
class GQLValidationRunFindingsFieldContributor(
    private val findingQueryService: FindingQueryService,
    private val gqlTypeFindingObservation: GQLTypeFindingObservation,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition>? = if (projectEntityType == ProjectEntityType.VALIDATION_RUN) {
        listOf(
            GraphQLFieldDefinition.newFieldDefinition()
                .name("findings")
                .description(
                    "Security findings reported by this run, each with its observation by this run " +
                            "(the severity, the installed version, the acceptance), the most severe first. " +
                            "Empty for a run which is not a security scan, and for a user who is not granted " +
                            "the view of the findings of the project."
                )
                .type(listType(gqlTypeFindingObservation.typeRef))
                .dataFetcher { env ->
                    val run: ValidationRun = env.getSource()!!
                    findingQueryService.getValidationRunFindings(run)
                }
                .build()
        )
    } else {
        null
    }
}
