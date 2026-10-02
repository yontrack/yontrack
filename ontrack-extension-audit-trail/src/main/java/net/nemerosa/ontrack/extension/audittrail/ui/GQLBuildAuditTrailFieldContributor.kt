package net.nemerosa.ontrack.extension.audittrail.ui

import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.audittrail.service.TrailService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Component

/**
 * `Build.auditTrail` — the trail of the build.
 *
 * The field is always in the schema, whatever the licence: a trail stays readable and verifiable
 * after the licence lapses. It is `null` when the build has no trail to read — the licence is off
 * and no entry was ever written for it.
 */
@Component
class GQLBuildAuditTrailFieldContributor(
    private val gqlTypeBuildAuditTrail: GQLTypeBuildAuditTrail,
    private val trailService: TrailService,
) : GQLProjectEntityFieldContributor {

    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition>? =
        if (projectEntityType == ProjectEntityType.BUILD) {
            listOf(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("auditTrail")
                    .description("Trail of the build: its entries, their endorsements and its verification - null when the build has no trail, the licence being off")
                    .type(gqlTypeBuildAuditTrail.typeRef)
                    .dataFetcher { env ->
                        val build: Build = env.getSource()!!
                        if (trailService.isTrailAvailable(build)) {
                            BuildAuditTrail(build)
                        } else {
                            null
                        }
                    }
                    .build()
            )
        } else {
            null
        }
}
