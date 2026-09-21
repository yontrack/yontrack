package net.nemerosa.ontrack.graphql.schema

import graphql.Scalars.*
import graphql.schema.*
import graphql.schema.GraphQLArgument.newArgument
import graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import graphql.schema.GraphQLObjectType.newObject
import net.nemerosa.ontrack.common.and
import net.nemerosa.ontrack.extension.api.ExtensionManager
import net.nemerosa.ontrack.graphql.schema.authorizations.GQLInterfaceAuthorizableService
import net.nemerosa.ontrack.graphql.support.*
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.model.labels.Label
import net.nemerosa.ontrack.model.labels.LabelManagementService
import net.nemerosa.ontrack.model.labels.LabelNotFoundException
import net.nemerosa.ontrack.model.labels.ProjectLabelManagementService
import net.nemerosa.ontrack.model.pagination.PageRequest
import net.nemerosa.ontrack.model.structure.*
import net.nemerosa.ontrack.model.support.FreeTextAnnotatorContributor
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
class GQLTypeBuild(
    private val gqlInterfaceAuthorizableService: GQLInterfaceAuthorizableService,
    private val structureService: StructureService,
    private val projectEntityInterface: GQLProjectEntityInterface,
    private val validation: GQLTypeValidation,
    private val validationRun: GQLTypeValidationRun,
    private val runInfo: GQLTypeRunInfo,
    private val gqlEnumValidationRunSortingMode: GQLEnumValidationRunSortingMode,
    private val runInfoService: RunInfoService,
    private val paginatedListFactory: GQLPaginatedListFactory,
    private val labelManagementService: LabelManagementService,
    private val validationStampService: ValidationStampService,
    private val projectLabelManagementService: ProjectLabelManagementService,
    creation: GQLTypeCreation,
    projectEntityFieldContributors: List<GQLProjectEntityFieldContributor>,
    freeTextAnnotatorContributors: List<FreeTextAnnotatorContributor>,
    extensionManager: ExtensionManager,
) : AbstractGQLProjectEntity<Build>(
    Build::class.java,
    ProjectEntityType.BUILD,
    projectEntityFieldContributors,
    creation,
    freeTextAnnotatorContributors,
    extensionManager,
) {

    override fun getTypeName() = BUILD

    override fun createType(cache: GQLTypeCache): GraphQLObjectType {
        return newObject()
            .name(BUILD)
            .withInterface(projectEntityInterface.typeRef)
            .fields(projectEntityInterfaceFields())
            // Authorizations
            .apply {
                gqlInterfaceAuthorizableService.apply(this, Build::class)
            }
            // Ref to branch
            .field(
                newFieldDefinition()
                    .name("branch")
                    .description("Reference to branch")
                    .type(GraphQLTypeReference(GQLTypeBranch.BRANCH))
                    .build()
            )
            // Promotion runs
            .field(
                newFieldDefinition()
                    .name("promotionRuns")
                    .description("Promotions for this build")
                    .argument(
                        newArgument()
                            .name(ARG_PROMOTION)
                            .description("Name of the promotion level")
                            .type(GraphQLString)
                            .build()
                    )
                    .argument(
                        newArgument()
                            .name(ARG_LAST_PER_LEVEL)
                            .description("Returns the last promotion run per promotion level")
                            .type(GraphQLBoolean)
                            .build()
                    )
                    .type(listType(GraphQLTypeReference(GQLTypePromotionRun.PROMOTION_RUN)))
                    .dataFetcher(buildPromotionRunsFetcher())
                    .build()
            )
            // Validation runs
            .field(
                newFieldDefinition()
                    .name("validationRuns")
                    .description("Validations for this build")
                    .argument(
                        newArgument()
                            .name(ARG_VALIDATION_STAMP)
                            .description("Name of the validation stamp, can be a regular expression.")
                            .type(GraphQLString)
                            .build()
                    )
                    .argument(
                        newArgument()
                            .name(ARG_COUNT)
                            .description("Maximum number of validation runs")
                            .type(GraphQLInt)
                            .defaultValue(50)
                            .build()
                    )
                    .type(listType(GraphQLTypeReference(GQLTypeValidationRun.VALIDATION_RUN)))
                    .dataFetcher(buildValidationRunsFetcher())
                    .build()
            )

            // Paginated list of validation runs
            .field(
                paginatedListFactory.createPaginatedField<Build, ValidationRun>(
                    cache = cache,
                    fieldName = "validationRunsPaginated",
                    fieldDescription = "Paginated list of validation runs",
                    itemType = validationRun.typeName,
                    arguments = listOf(
                        newArgument()
                            .name(ARG_SORTING_MODE)
                            .description("Describes how the validation runs must be sorted.")
                            .type(gqlEnumValidationRunSortingMode.getTypeRef())
                            .build(),
                        newArgument()
                            .name(ARG_STATUSES)
                            .description("List of statuses to select")
                            .type(GraphQLList(GraphQLString))
                            .build(),
                        newArgument()
                            .name(ARG_VALIDATION_STAMP)
                            .description("Validation stamp to filter upon")
                            .type(GraphQLString)
                            .build()
                    ),
                    itemListCounter = { env, build ->
                        val vsName: String? = env.getArgument(ARG_VALIDATION_STAMP)
                        if (vsName.isNullOrBlank()) {
                            structureService.getValidationRunsCountForBuild(
                                buildId = build.id,
                                statuses = env.getArgument<List<String>>(ARG_STATUSES),
                            )
                        } else {
                            val vs = structureService.findValidationStampByName(
                                build.project.name,
                                build.branch.name,
                                vsName
                            ).getOrNull() ?: return@createPaginatedField 0
                            structureService.getValidationRunsCountForBuildAndValidationStamp(
                                buildId = build.id,
                                validationStampId = vs.id,
                                statuses = env.getArgument<List<String>>(ARG_STATUSES),
                            )
                        }
                    },
                    itemListProvider = { env, build, offset, size ->
                        val vsName: String? = env.getArgument(ARG_VALIDATION_STAMP)
                        val sortingMode = env.getArgument<String>(ARG_SORTING_MODE)
                            ?.let { ValidationRunSortingMode.valueOf(it) }
                            ?: ValidationRunSortingMode.ID
                        if (vsName.isNullOrBlank()) {
                            structureService.getValidationRunsForBuild(
                                buildId = build.id,
                                offset = offset,
                                count = size,
                                sortingMode = sortingMode,
                                statuses = env.getArgument<List<String>>(ARG_STATUSES),
                            )
                        } else {
                            val vs = structureService.findValidationStampByName(
                                build.project.name,
                                build.branch.name,
                                vsName
                            ).getOrNull() ?: return@createPaginatedField emptyList()
                            structureService.getValidationRunsForBuildAndValidationStamp(
                                build = build,
                                validationStamp = vs,
                                offset = offset,
                                count = size,
                                sortingMode = sortingMode,
                                statuses = env.getArgument<List<String>>(ARG_STATUSES),
                            )
                        }
                    }
                )
            )
            // Validation runs per validation stamp
            .field { f ->
                f.name("validations")
                    .description("Validations per validation stamp")
                    .argument(
                        newArgument()
                            .name(ARG_VALIDATION_STAMP)
                            .description("Name of the validation stamp")
                            .type(GraphQLString)
                            .build()
                    )
                    .argument(
                        stringListArgument(
                            name = ARG_VALIDATION_STAMPS,
                            description = "List of validation stamps",
                            nullable = true,
                        )
                    )
                    .argument {
                        it.name(GQLPaginatedListFactory.ARG_OFFSET)
                            .description("Offset for the page")
                            .type(GraphQLInt)
                            .defaultValue(0)
                    }
                    .argument {
                        it.name(GQLPaginatedListFactory.ARG_SIZE)
                            .description("Size of the page")
                            .type(GraphQLInt)
                            .defaultValue(PageRequest.DEFAULT_PAGE_SIZE)
                    }
                    .type(listType(validation.typeRef))
                    .dataFetcher(buildValidationsFetcher())
            }
            // Build links - "using" direction, with pagination
            .field(
                paginatedListFactory.createPaginatedField<Build, BuildLink>(
                    cache = cache,
                    fieldName = "usingQualified",
                    fieldDescription = "List of builds being used by this one.",
                    itemType = BuildLink::class.java.simpleName,
                    arguments = listOf(
                        newArgument()
                            .name("project")
                            .description("Keeps only links targeted from this project")
                            .type(GraphQLString)
                            .build(),
                        newArgument()
                            .name("qualifier")
                            .description("Keeps only links targeted for this qualifier")
                            .type(GraphQLString)
                            .build(),
                        newArgument()
                            .name("branch")
                            .description("Keeps only links targeted from this branch. `project` argument is also required.")
                            .type(GraphQLString)
                            .build(),
                        intArgument(
                            name = "depth",
                            description = "If greater than 0, looks for children up to this depth",
                            nullable = true,
                            defaultValue = 0,
                        ),
                        stringArgument(
                            name = "label",
                            description = "Label (category:name) to filter build projects with",
                            nullable = true,
                            defaultValue = "",
                        ),
                        booleanArgument(
                            name = ARG_PROJECT_FRAGMENT,
                            description = "Using a fragment of the project name",
                            nullable = true,
                        ),
                    ),
                    itemPaginatedListProvider = { environment, build, offset, size ->
                        val depth = environment.getArgument<Int>("depth") ?: 0
                        var filter: (BuildLink) -> Boolean = getFilter(environment)
                        val label: String? = environment.getArgument("label")
                        if (!label.isNullOrBlank()) {
                            val (labelCategory, labelName) = Label.categoryAndNameFromDisplay(label)
                            val actualLabel = labelManagementService.findLabels(labelCategory, labelName).firstOrNull()
                                ?: throw LabelNotFoundException(labelCategory, labelName)
                            filter = filter and {
                                projectLabelManagementService.hasProjectLabel(it.build.project, actualLabel)
                            }
                        }
                        structureService.getQualifiedBuildsUsedBy(
                            build = build,
                            offset = offset,
                            size = size,
                            depth = depth,
                            filter = filter,
                        )
                    }
                )
            )
            // Build links - "usedBy" direction, with pagination
            .field(
                paginatedListFactory.createPaginatedField<Build, BuildLink>(
                    cache = cache,
                    fieldName = "usedByQualified",
                    fieldDescription = "List of builds using this one.",
                    itemType = BuildLink::class.java.simpleName,
                    arguments = listOf(
                        newArgument()
                            .name("project")
                            .description("Keeps only links targeted from this project")
                            .type(GraphQLString)
                            .build(),
                        newArgument()
                            .name("branch")
                            .description("Keeps only links targeted from this branch. `project` argument is also required.")
                            .type(GraphQLString)
                            .build(),
                        booleanArgument(
                            name = ARG_PROJECT_FRAGMENT,
                            description = "Using a fragment of the project name",
                            nullable = true,
                        ),
                    ),
                    itemPaginatedListProvider = { environment, build, offset, size ->
                        val filter = getFilter(environment)
                        structureService.getQualifiedBuildsUsing(
                            build,
                            offset,
                            size,
                            filter
                        )
                    }
                )
            )
            // Run info
            .field {
                it.name("runInfo")
                    .description("Run info associated with this build")
                    .type(runInfo.typeRef)
                    .runInfoFetcher<Build> { entity -> runInfoService.getRunInfo(entity) }
            }
            // Previous build
            .field {
                it.name("previousBuild")
                    .description("Previous build")
                    .type(GraphQLTypeReference(BUILD))
                    .dataFetcher { env ->
                        val build: Build = env.getSource()!!
                        structureService.getPreviousBuild(build.id)
                    }
            }
            // Next build
            .field {
                it.name("nextBuild")
                    .description("Next build")
                    .type(GraphQLTypeReference(BUILD))
                    .dataFetcher { env ->
                        val build: Build = env.getSource()!!
                        structureService.getNextBuild(build.id)
                    }
            }
            // OK
            .build()
    }

    private fun getFilter(environment: DataFetchingEnvironment): (BuildLink) -> Boolean {
        val projectName: String? = environment.getArgument("project")
        val projectFragment: Boolean = environment.getArgument(ARG_PROJECT_FRAGMENT) ?: false
        val qualifier: String? = environment.getArgument("qualifier")
        val branchName: String? = environment.getArgument("branch")
        val filter: (BuildLink) -> Boolean = if (branchName != null) {
            if (projectName.isNullOrBlank()) {
                throw IllegalArgumentException("`project` is required")
            } else {
                {
                    matchProjectName(it, projectName, projectFragment) &&
                            it.build.branch.name == branchName &&
                            (qualifier == null || qualifier == it.qualifier)
                }
            }
        } else if (!projectName.isNullOrBlank()) {
            {
                matchProjectName(it, projectName, projectFragment) &&
                        (qualifier == null || qualifier == it.qualifier)
            }
        } else {
            { true }
        }
        return filter
    }

    private fun matchProjectName(link: BuildLink, projectName: String, projectFragment: Boolean): Boolean =
        if (projectFragment) {
            link.build.branch.project.name.contains(projectName, ignoreCase = true)
        } else {
            link.build.branch.project.name == projectName
        }

    private fun buildValidationsFetcher() =
        DataFetcher { environment ->
            val build: Build = environment.getSource()!!
            // Filter on validation stamp
            val validationStampName: String? = environment.getArgument(ARG_VALIDATION_STAMP)
            val validationStampNames: List<String>? = environment.getArgument(ARG_VALIDATION_STAMPS)
            val offset = environment.getArgument<Int>(GQLPaginatedListFactory.ARG_OFFSET) ?: 0
            val size = environment.getArgument<Int>(GQLPaginatedListFactory.ARG_SIZE) ?: 10

            // Gets the list of validation stamps
            val actualValidationStampNames = if (!validationStampNames.isNullOrEmpty()) {
                if (!validationStampName.isNullOrBlank()) {
                    validationStampNames + validationStampName
                } else {
                    validationStampNames
                }
            } else if (!validationStampName.isNullOrBlank()) {
                listOf(validationStampName)
            } else {
                null
            }

            // Using the validation stamp names
            val validationStamps = if (!actualValidationStampNames.isNullOrEmpty()) {
                validationStampService.findValidationStampsForNames(
                    branch = build.branch,
                    validationStamps = actualValidationStampNames,
                )
            }
            // Using all the validation stamps
            else {
                structureService.getValidationStampListForBranch(build.branch.id)
            }

            // Collecting the validations
            validationStamps.map { validationStamp ->
                buildValidation(validationStamp, build, offset, size)
            }
        }

    private fun buildValidation(
        validationStamp: ValidationStamp,
        build: Build,
        offset: Int,
        size: Int
    ): GQLTypeValidation.GQLTypeValidationData {
        return GQLTypeValidation.GQLTypeValidationData(
            validationStamp,
            structureService.getValidationRunsForBuildAndValidationStamp(
                build,
                validationStamp,
                offset,
                size
            )
        )
    }

    private fun buildValidationRunsFetcher() =
        DataFetcher { environment ->
            val build: Build = environment.getSource()!!
            // Filter
            val count: Int = environment.getArgument(ARG_COUNT) ?: 50
            val validation: String? = environment.getArgument(ARG_VALIDATION_STAMP)
            if (validation != null) {
                // Gets one validation stamp by name
                val validationStamp = structureService.findValidationStampByName(
                    build.project.name,
                    build.branch.name,
                    validation
                ).getOrNull()
                // If there is one, we return the list of runs for this very stamp
                if (validationStamp != null) {
                    // Gets validations runs for this validation level
                    return@DataFetcher structureService.getValidationRunsForBuildAndValidationStamp(
                        build.id,
                        validationStamp.id,
                        0,
                        count
                    )
                }
                // If not, we collect the list of matching validation stamp, assuming
                // the argument is a regular expression
                else {
                    val vsNameRegex = validation.toRegex()
                    return@DataFetcher structureService.getValidationStampListForBranch(build.branch.id)
                        .filter { vs -> vsNameRegex.matches(vs.name) }
                        .flatMap { vs ->
                            structureService.getValidationRunsForBuildAndValidationStamp(
                                build.id,
                                vs.id,
                                0, count
                            )
                        }
                }
            } else {
                // Gets all the validation runs (limited by count)
                return@DataFetcher structureService.getValidationRunsForBuild(build.id, 0, count)
                    .take(count)
            }
        }

    private fun buildPromotionRunsFetcher() =
        DataFetcher<List<PromotionRun>> { environment ->
            val build: Build = environment.getSource()!!
            // Last per promotion filter?
            val lastPerLevel: Boolean = environment.getArgument(ARG_LAST_PER_LEVEL) ?: false
            // Promotion filter
            val promotion: String? = environment.getArgument(ARG_PROMOTION)
            val promotionLevel: PromotionLevel? = if (promotion != null) {
                // Gets the promotion level
                structureService.findPromotionLevelByName(
                    build.project.name,
                    build.branch.name,
                    promotion
                ).orElse(null)
            } else {
                null
            }
            if (promotionLevel != null) {
                // Gets promotion runs for this promotion level
                if (lastPerLevel) {
                    structureService.getLastPromotionRunForBuildAndPromotionLevel(build, promotionLevel)
                        .map { listOf(it) }
                        .orElse(listOf())
                } else {
                    structureService.getPromotionRunsForBuildAndPromotionLevel(build, promotionLevel)
                }
            } else {
                // Gets all the promotion runs
                if (lastPerLevel) {
                    // Getting the promotion levels of the branch
                    val promotionLevels = structureService.getPromotionLevelListForBranch(build.branch.id)
                    // Use the build & cached promotion levels to get the promotion runs
                    structureService.getLastPromotionRunsForBuild(build, promotionLevels)
                } else {
                    structureService.getPromotionRunsForBuild(build.id)
                }
            }
        }

    override fun getSignature(entity: Build): Signature? {
        return entity.signature
    }

    companion object {
        /**
         * Name of the type
         */
        const val BUILD = "Build"

        /**
         * Filter on the validation runs
         */
        const val ARG_VALIDATION_STAMP = "validationStamp"

        /**
         * Filter on a list of validation stamp names
         */
        const val ARG_VALIDATION_STAMPS = "validationStamps"

        /**
         * Count argument
         */
        const val ARG_COUNT = "count"

        /**
         * Promotion level argument
         */
        const val ARG_PROMOTION = "promotion"

        /**
         * Last per level argument
         */
        const val ARG_LAST_PER_LEVEL = "lastPerLevel"

        /**
         * Sorting mode for the validation runs
         */
        const val ARG_SORTING_MODE = "sortingMode"

        /**
         * Filter on statuses
         */
        const val ARG_STATUSES = "statuses"

        /**
         * Using a fragment of the project name
         */
        const val ARG_PROJECT_FRAGMENT = "projectFragment"
    }
}
