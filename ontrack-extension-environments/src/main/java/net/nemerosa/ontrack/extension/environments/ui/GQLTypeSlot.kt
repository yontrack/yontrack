package net.nemerosa.ontrack.extension.environments.ui

import graphql.Scalars.GraphQLBoolean
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.environments.Slot
import net.nemerosa.ontrack.extension.environments.SlotPipeline
import net.nemerosa.ontrack.extension.environments.SlotPipelineStatus
import net.nemerosa.ontrack.extension.environments.service.SlotService
import net.nemerosa.ontrack.extension.environments.service.SlotStatus
import net.nemerosa.ontrack.extension.environments.service.SlotStatusService
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflow
import net.nemerosa.ontrack.extension.environments.workflows.SlotWorkflowService
import net.nemerosa.ontrack.graphql.schema.GQLType
import net.nemerosa.ontrack.graphql.schema.GQLTypeBuild
import net.nemerosa.ontrack.graphql.schema.GQLTypeCache
import net.nemerosa.ontrack.graphql.schema.authorizations.GQLInterfaceAuthorizableService
import net.nemerosa.ontrack.graphql.support.*
import net.nemerosa.ontrack.graphql.support.pagination.GQLPaginatedListFactory
import net.nemerosa.ontrack.model.structure.Build
import org.dataloader.DataLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

@Component
class GQLTypeSlot(
    private val slotService: SlotService,
    private val gqlTypeSlotPipeline: GQLTypeSlotPipeline,
    private val gqlTypeSlotAdmissionRuleConfig: GQLTypeSlotAdmissionRuleConfig,
    private val gqlInterfaceAuthorizableService: GQLInterfaceAuthorizableService,
    private val paginatedListFactory: GQLPaginatedListFactory,
    private val slotWorkflowService: SlotWorkflowService,
    private val slotStatusService: SlotStatusService,
) : GQLType {

    override fun getTypeName(): String = Slot::class.java.simpleName

    override fun createType(cache: GQLTypeCache): GraphQLObjectType =
        GraphQLObjectType.newObject()
            .name(typeName)
            .description("Deployment slot into an environment")
            .idFieldForString(Slot::id)
            .stringField(Slot::description)
            .field(Slot::project)
            .stringField(Slot::qualifier)
            .field(Slot::environment)
            // Authorizations
            .apply {
                gqlInterfaceAuthorizableService.apply(this, Slot::class)
            }
            // Last eligible build
            .field {
                it.name("eligibleBuild")
                    .description(
                        "Last build for this slot. By default, the last build which can be deployed now. " +
                                "With `deployable: false`, the last build which is merely eligible - a pipeline can be started for it, " +
                                "but it waits as a candidate until the admission rules accept it."
                    )
                    .argument(deployableArgument())
                    .type(GraphQLTypeReference(GQLTypeBuild.BUILD))
                    .dataFetcher { env ->
                        val slot: Slot = env.getSource()!!
                        slotService.getEligibleBuilds(slot, count = 1, deployable = env.deployable)
                            .pageItems.firstOrNull()
                    }
            }
            // Paginated list of eligible builds
            .field(
                paginatedListFactory.createPaginatedField<Slot, Build>(
                    cache = cache,
                    fieldName = "eligibleBuilds",
                    fieldDescription = "Paginated list of builds for this slot, newest first. By default, the builds which can be deployed now. " +
                            "With `deployable: false`, the builds which are merely eligible - a pipeline can be started for them, " +
                            "but it waits as a candidate until the admission rules accept it.",
                    arguments = listOf(deployableArgument()),
                    itemType = GQLTypeBuild.BUILD,
                    itemPaginatedListProvider = { env, slot, offset, size ->
                        slotService.getEligibleBuilds(slot, offset = offset, count = size, deployable = env.deployable)
                    }
                )
            )
            // Current pipeline
            .field {
                it.name("currentPipeline")
                    .description("Current pipeline in the slot")
                    .type(gqlTypeSlotPipeline.typeRef)
                    .dataFetcher { env -> status(env) { it.currentPipeline } }
            }
            // Last deployed pipeline
            .field {
                it.name("lastDeployedPipeline")
                    .description("Last deployed pipeline in the slot")
                    .type(gqlTypeSlotPipeline.typeRef)
                    .dataFetcher { env -> status(env) { it.lastDeployedPipeline } }
            }
            // Paginated list of pipelines
            .field(
                paginatedListFactory.createPaginatedField<Slot, SlotPipeline>(
                    cache = cache,
                    fieldName = "pipelines",
                    fieldDescription = "Paginated list of pipelines",
                    arguments = listOf(
                        intArgument("buildId", "Filtering on a build"),
                        stringArgument(ARG_BUILD_NAME, "Filtering on the name of a build"),
                        stringArgument(ARG_BRANCH_NAME, "Name of the branch to get the pipelines for"),
                        booleanArgument(ARG_DONE, "Filtering on finished pipelines"),
                        enumArgument<SlotPipelineStatus>(ARG_STATUS, "Filtering on an exact status"),
                        stringArgument(
                            ARG_USER,
                            "Filtering on a user who acted on the deployment, anywhere in its audit trail"
                        ),
                    ),
                    itemType = gqlTypeSlotPipeline.typeName,
                    itemPaginatedListProvider = { env, slot, offset, size ->
                        val buildId: Int? = env.getArgument("buildId")
                        val buildName: String? = env.getArgument(ARG_BUILD_NAME)
                        val branchName: String? = env.getArgument(ARG_BRANCH_NAME)
                        val done: Boolean? = env.getArgument(ARG_DONE)
                        val status = env.getArgument<String?>(ARG_STATUS)?.let { SlotPipelineStatus.valueOf(it) }
                        val user: String? = env.getArgument(ARG_USER)
                        slotService.findPipelines(
                            slot = slot,
                            offset = offset,
                            size = size,
                            buildId = buildId,
                            buildName = buildName,
                            branchName = branchName,
                            done = done,
                            status = status,
                            user = user,
                        )
                    }
                )
            )
            // List of rules
            .field {
                it.name("admissionRules")
                    .description("List of configured admission rules for this slot")
                    .type(listType(gqlTypeSlotAdmissionRuleConfig.typeRef))
                    .dataFetcher { env ->
                        val slot: Slot = env.getSource()!!
                        slotService.getAdmissionRuleConfigs(slot)
                    }
            }
            // Blocked
            .field {
                it.name("blocked")
                    .description(
                        "Is the in-flight deployment of this slot held up by a failing, " +
                                "non-overridden admission rule or workflow? False when nothing is in flight."
                    )
                    .type(GraphQLBoolean.toNotNull())
                    .dataFetcher { env -> status(env) { it.blocked } }
            }
            // Behind
            .field {
                it.name("behind")
                    .description("Does a slot upstream of this one in the project's slot graph hold a newer build?")
                    .type(GraphQLBoolean.toNotNull())
                    .dataFetcher { env -> status(env) { it.behind } }
            }
            // Next builds
            .field {
                it.name("nextBuilds")
                    .description("Eligible builds newer than the one this slot holds, newest first")
                    .type(listType(GraphQLTypeReference(GQLTypeBuild.BUILD)))
                    .argument(
                        intArgument(
                            "count",
                            "Maximum number of builds to return (defaults to $DEFAULT_NEXT_BUILDS_COUNT)"
                        )
                    )
                    .dataFetcher { env ->
                        val slot: Slot = env.getSource()!!
                        val count: Int = env.getArgument("count") ?: DEFAULT_NEXT_BUILDS_COUNT
                        slotStatusService.getNextBuilds(slot, count = count)
                    }
            }
            // List of workflows
            .field {
                it.name("workflows")
                    .description("List of workflows for this slot")
                    .type(listType(SlotWorkflow::class.toTypeRef()))
                    .argument(enumArgument<SlotPipelineStatus>("trigger", "Type of trigger to filter on"))
                    .dataFetcher { env ->
                        val source: Slot = env.getSource()!!
                        val trigger = env.getArgument<String?>("trigger")?.let {
                            SlotPipelineStatus.valueOf(it)
                        }
                        slotWorkflowService.getSlotWorkflowsBySlot(source)
                            .filter { trigger == null || it.trigger == trigger }
                    }
            }
            // OK
            .build()

    /**
     * The four cell fields, all read from the one batched reading of the slot.
     *
     * `blocked`, `behind`, what is deployed and what is on its way are one answer
     * ([net.nemerosa.ontrack.extension.environments.service.SlotStatus]) computed together, so the
     * fields share a data loader rather than each asking the service on its own. That is what lets
     * the matrix select all four on a hundred slots without a hundred round trips - see
     * [SlotStatusDataLoader].
     */
    private fun <T> status(env: DataFetchingEnvironment, extract: (SlotStatus) -> T): CompletableFuture<T> {
        val slot: Slot = env.getSource()!!
        val loader: DataLoader<Slot, SlotStatus> =
            env.dataLoaderRegistry.getDataLoader(SlotStatusDataLoader.NAME)
                ?: error("No ${SlotStatusDataLoader.NAME} data loader is registered.")
        return loader.load(slot).thenApply(extract)
    }

    /**
     * `deployable` argument of the eligible builds: `true` by default, since a build which is
     * merely eligible cannot be deployed yet, and listing it as ready is what #1851 was about.
     */
    private fun deployableArgument(): GraphQLArgument =
        GraphQLArgument.newArgument(
            booleanArgument(
                ARG_DEPLOYABLE,
                "If true (the default), restricts the list of builds to the ones which can be deployed now. " +
                        "If false, returns all the eligible builds, including the ones which are not deployable yet."
            )
        )
            .defaultValueProgrammatic(true)
            .build()

    private val DataFetchingEnvironment.deployable: Boolean
        get() = getArgument<Boolean>(ARG_DEPLOYABLE) ?: true

    companion object {
        const val ARG_DEPLOYABLE = "deployable"
        const val ARG_BUILD_NAME = "buildName"
        const val ARG_BRANCH_NAME = "branchName"
        const val ARG_DONE = "done"
        const val ARG_STATUS = "status"
        const val ARG_USER = "user"

        /**
         * The "Next" section of the slot drawer shows up to three builds.
         */
        const val DEFAULT_NEXT_BUILDS_COUNT = 3
    }
}