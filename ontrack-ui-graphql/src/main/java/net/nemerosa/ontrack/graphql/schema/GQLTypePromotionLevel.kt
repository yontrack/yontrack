package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.common.and
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.graphql.schema.authorizations.GQLInterfaceAuthorizableService
import net.nemerosa.ontrack.graphql.support.*
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.model.pagination.PaginatedList
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.FreeTextAnnotatorContributor
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class GQLTypePromotionLevel(
    private val gqlInterfaceAuthorizableService: GQLInterfaceAuthorizableService,
    private val structureService: StructureService,
    creation: GQLTypeCreation,
    private val promotionRun: GQLTypePromotionRun,
    projectEntityFieldContributors: List<GQLProjectEntityFieldContributor>,
    private val projectEntityInterface: GQLProjectEntityInterface,
    private val paginatedListFactory: GQLPaginatedListFactory,
    private val buildDisplayNameService: BuildDisplayNameService,
    freeTextAnnotatorContributors: List<FreeTextAnnotatorContributor>,
    extensionManager: ExtensionManager,
) : AbstractGQLProjectEntity<PromotionLevel>(
        PromotionLevel::class.java,
        ProjectEntityType.PROMOTION_LEVEL,
        projectEntityFieldContributors,
        creation,
        freeTextAnnotatorContributors,
        extensionManager,
) {

    override fun getTypeName(): String = PROMOTION_LEVEL

    override fun createType(cache: GQLTypeCache): GraphQLObjectType = GraphQLObjectType.newObject()
            .name(PROMOTION_LEVEL)
            .withInterface(projectEntityInterface.typeRef)
            .fields(projectEntityInterfaceFields())
            // Authorizations
            .apply {
                gqlInterfaceAuthorizableService.apply(this, PromotionLevel::class)
            }
            // Image flag
            .field {
                it.name("image")
                        .description("Flag to indicate if an image is associated")
                        .type(Scalars.GraphQLBoolean)
            }
            // Ref to branch
            .field {
                it.name("branch")
                        .description("Reference to branch")
                        .type(GraphQLTypeReference(GQLTypeBranch.BRANCH))
            }
            // Promotion runs
            .field {
                it.name("promotionRuns")
                        .deprecate("Use the paginated promotion runs with the `promotionRunsPaginated` field.")
                        .description("List of runs for this promotion")
                        .type(listType(promotionRun.typeRef))
                        .arguments(listArguments())
                        .dataFetcher(promotionLevelPromotionRunsFetcher())
            }
            // Paginated promotion runs
            .field(
                    paginatedListFactory.createPaginatedField<PromotionLevel, PromotionRun>(
                            cache = cache,
                            fieldName = "promotionRunsPaginated",
                            fieldDescription = "Paginated list of promotion runs",
                            itemType = promotionRun.typeName,
                            arguments = listOf(
                                    dateTimeArgument(ARG_BEFORE_DATE, "Keeps only runs before this data / time"),
                                    dateTimeArgument(ARG_AFTER_DATE, "Keeps only runs after this data / time"),
                                    stringArgument(ARG_NAME, "Regular expression on the name of the build name"),
                                    stringArgument(ARG_VERSION, "Regular expression on the release property attached to the build name")
                            ),
                            itemPaginatedListProvider = { env, promotionLevel, offset, size ->
                                val beforeDate: LocalDateTime? = env.getArgument(ARG_BEFORE_DATE)
                                val afterDate: LocalDateTime? = env.getArgument(ARG_AFTER_DATE)
                                val name: String? = env.getArgument(ARG_NAME)
                                val version: String? = env.getArgument(ARG_VERSION)
                                // Promotion run filter
                                var filter: (PromotionRun) -> Boolean = { true }
                                if (beforeDate != null) {
                                    filter = filter and { run ->
                                        run.signature.time <= beforeDate
                                    }
                                }
                                if (afterDate != null) {
                                    filter = filter and { run ->
                                        run.signature.time >= afterDate
                                    }
                                }
                                if (!name.isNullOrBlank()) {
                                    val r = name.toRegex()
                                    filter = filter and { run ->
                                        run.build.name.matches(r)
                                    }
                                }
                                if (!version.isNullOrBlank()) {
                                    val r = version.toRegex()
                                    filter = filter and { run ->
                                        val buildVersion = buildDisplayNameService.getFirstBuildDisplayName(run.build)
                                        buildVersion != null && buildVersion.matches(r)
                                    }
                                }
                                // Gets the filtered list of promotion runs
                                val runs = structureService.getPromotionRunsForPromotionLevel(promotionLevel.id)
                                        .filter(filter)
                                // Pagination
                                PaginatedList.Companion.create(runs, offset, size)
                            }
                    )
            )
            // Configurable fields
            .field {
                it.name("fields")
                    .description("Configurable fields defined on this promotion level")
                    .type(listType(GQLTypePromotionLevelField.PROMOTION_LEVEL_FIELD))
                    .dataFetcher { env ->
                        val pl = env.getSource<PromotionLevel>()!!
                        if (pl.fields.isNotEmpty()) {
                            pl.fields
                        } else {
                            structureService.getPromotionLevel(pl.id).fields
                        }
                    }
            }
            // OK
            .build()

    private fun promotionLevelPromotionRunsFetcher(): DataFetcher<List<PromotionRun>> =
            DataFetcher { environment: DataFetchingEnvironment ->
                val promotionLevel = environment.getSource<PromotionLevel>()!!
                // Gets all the promotion runs
                val promotionRuns = structureService.getPromotionRunsForPromotionLevel(promotionLevel.id)
                // Filters according to the arguments
                stdListArgumentsFilter(promotionRuns, environment)
            }

    override fun getSignature(entity: PromotionLevel): Signature? {
        return entity.signature
    }

    companion object {
        const val PROMOTION_LEVEL = "PromotionLevel"

        // Filtering for the paginated promotion runs

        const val ARG_AFTER_DATE = "afterDate"
        const val ARG_BEFORE_DATE = "beforeDate"
        const val ARG_NAME = "name"
        const val ARG_VERSION = "version"
    }

}