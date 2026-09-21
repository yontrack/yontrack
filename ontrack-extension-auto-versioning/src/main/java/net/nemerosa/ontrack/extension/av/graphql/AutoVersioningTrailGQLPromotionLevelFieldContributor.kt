package net.nemerosa.ontrack.extension.av.graphql

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import net.nemerosa.ontrack.extension.av.graphql.AutoVersioningTrailGQLPromotionRunFieldContributor.Companion.ARG_FILTER
import net.nemerosa.ontrack.extension.av.listener.AutoVersioningPromotionListenerService
import net.nemerosa.ontrack.extension.av.tracking.AutoVersioningBranchTrail
import net.nemerosa.ontrack.extension.av.tracking.AutoVersioningTrackingService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import net.nemerosa.ontrack.model.structure.PromotionLevel
import org.springframework.stereotype.Component

@Component
class AutoVersioningTrailGQLPromotionLevelFieldContributor(
    private val autoVersioningPromotionListenerService: AutoVersioningPromotionListenerService,
    private val autoVersioningTrackingService: AutoVersioningTrackingService,
    private val gqlTypeAutoVersioningTrail: GQLTypeAutoVersioningTrail,
    private val paginatedListFactory: GQLPaginatedListFactory,
    private val gqlInputAutoVersioningTrailFilter: GQLInputAutoVersioningTrailFilter,
) : GQLProjectEntityFieldContributor {
    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition>? = if (projectEntityType == ProjectEntityType.PROMOTION_LEVEL) {
        listOf(
            GraphQLFieldDefinition.newFieldDefinition()
                .name("autoVersioningTrail")
                .description("List of branches targeted for auto-versioning based on this promotion level or with their reason for rejection")
                .deprecate("Will be removed in V6. Use autoVersioningTrailPaginated instead.")
                .type(gqlTypeAutoVersioningTrail.typeRef)
                .dataFetcher { env ->
                    val pl: PromotionLevel = env.getSource()!!
                    val tracking = autoVersioningTrackingService.startInMemoryTrail()
                    autoVersioningPromotionListenerService.getConfiguredBranches(pl, tracking)
                    tracking.trail
                }
                .build(),
            paginatedListFactory.createPaginatedField<PromotionLevel, AutoVersioningBranchTrail>(
                cache = GQLTypeCache(),
                fieldName = "autoVersioningTrailPaginated",
                fieldDescription = "List of branches targeted for auto-versioning based on this promotion or with their reason for rejection",
                itemType = AutoVersioningBranchTrail::class.java.simpleName,
                itemTypeSuffix = "Promotion",
                arguments = listOf(
                    GraphQLArgument.newArgument()
                        .name(ARG_FILTER)
                        .type(gqlInputAutoVersioningTrailFilter.typeRef)
                        .build()
                ),
                itemPaginatedListProvider = { env, pl, offset, size ->
                    val filter = env.getArgument<Any>(ARG_FILTER)
                        .let { gqlInputAutoVersioningTrailFilter.convert(it) }
                    autoVersioningTrackingService.getPromotionPaginatedTrail(
                        pl,
                        filter = filter,
                        offset = offset,
                        size = size
                    )
                }
            )
        )
    } else {
        null
    }

}