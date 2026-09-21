package net.nemerosa.ontrack.extension.av.graphql

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.av.tracking.AutoVersioningBranchTrail
import net.nemerosa.ontrack.extension.av.tracking.AutoVersioningTrackingService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PromotionRun
import org.springframework.stereotype.Component

@Component
class AutoVersioningTrailGQLPromotionRunFieldContributor(
    private val autoVersioningTrackingService: AutoVersioningTrackingService,
    private val gqlTypeAutoVersioningTrail: GQLTypeAutoVersioningTrail,
    private val paginatedListFactory: GQLPaginatedListFactory,
    private val gqlInputAutoVersioningTrailFilter: GQLInputAutoVersioningTrailFilter,
) : GQLProjectEntityFieldContributor {
    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition>? = if (projectEntityType == ProjectEntityType.PROMOTION_RUN) {
        listOf(
            GraphQLFieldDefinition.newFieldDefinition()
                .name("autoVersioningTrail")
                .description("List of branches targeted for auto-versioning based on this promotion run or with their reason for rejection")
                .deprecate("Will be removed in V6. Use autoVersioningTrailPaginated instead.")
                .type(gqlTypeAutoVersioningTrail.typeRef)
                .dataFetcher { env ->
                    val run: PromotionRun = env.getSource()!!
                    autoVersioningTrackingService.getTrail(run)
                }
                .build(),
            paginatedListFactory.createPaginatedField<PromotionRun, AutoVersioningBranchTrail>(
                cache = GQLTypeCache(),
                fieldName = "autoVersioningTrailPaginated",
                fieldDescription = "List of branches targeted for auto-versioning based on this promotion run or with their reason for rejection",
                itemType = AutoVersioningBranchTrail::class.java.simpleName,
                arguments = listOf(
                    GraphQLArgument.newArgument()
                        .name(ARG_FILTER)
                        .type(gqlInputAutoVersioningTrailFilter.typeRef)
                        .build()
                ),
                itemPaginatedListProvider = { env, run, offset, size ->
                    val filter = env.getArgument<Any>(ARG_FILTER)
                        .let { gqlInputAutoVersioningTrailFilter.convert(it) }
                    autoVersioningTrackingService.getPaginatedTrail(run, filter = filter, offset = offset, size = size)
                }
            )
        )
    } else {
        null
    }

    companion object {
        const val ARG_FILTER = "filter"
    }

}