package net.nemerosa.ontrack.extension.environments.ui

import graphql.Scalars.GraphQLBoolean
import graphql.schema.DataFetchingEnvironment
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
                    .description("Last eligible build for this slot")
                    .type(GraphQLTypeReference(GQLTypeBuild.BUILD))
                    .dataFetcher { env ->
                        val slot: Slot = env.getSource()!!
                        slotService.getEligibleBuilds(slot, count = 1).pageItems.firstOrNull()
                    }
            }
            // Paginated list of eligible builds
            .field(
                paginatedListFactory.createPaginatedField<Slot, Build>(
                    cache = cache,
                    fieldName = "eligibleBuilds",
                    fieldDescription = "Paginated list of eligible builds",
                    arguments = listOf(
                        booleanArgument(
                            "deployable",
                            "If true, restricts the list of builds to the ones which can actually be deployed."
                        )
                    ),
                    itemType = GQLTypeBuild.BUILD,
                    itemPaginatedListProvider = { env, slot, offset, size ->
                        val deployable: Boolean = env.getArgument("deployable") ?: false
                        slotService.getEligibleBuilds(slot, offset = offset, count = size, deployable = deployable)
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
                        stringArgument(ARG_BRANCH_NAME, "Name of the branch to get the pipelines for"),
                        booleanArgument(ARG_DONE, "Filtering on finished pipelines"),
                    ),
                    itemType = gqlTypeSlotPipeline.typeName,
                    itemPaginatedListProvider = { env, slot, offset, size ->
                        val buildId: Int? = env.getArgument("buildId")
                        val branchName: String? = env.getArgument(ARG_BRANCH_NAME)
                        val done: Boolean? = env.getArgument(ARG_DONE)
                        slotService.findPipelines(
                            slot = slot,
                            offset = offset,
                            size = size,
                            buildId = buildId,
                            branchName = branchName,
                            done = done,
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

    companion object {
        const val ARG_BRANCH_NAME = "branchName"
        const val ARG_DONE = "done"

        /**
         * The "Next" section of the slot drawer shows up to three builds.
         */
        const val DEFAULT_NEXT_BUILDS_COUNT = 3
    }
}