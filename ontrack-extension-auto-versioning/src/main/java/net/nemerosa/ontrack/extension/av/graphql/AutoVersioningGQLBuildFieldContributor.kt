package net.nemerosa.ontrack.extension.av.graphql

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLTypeReference
import net.nemerosa.ontrack.extension.av.config.AutoVersioningConfigurationService
import net.nemerosa.ontrack.graphql.schema.GQLProjectEntityFieldContributor
import net.nemerosa.ontrack.graphql.support.enumArgument
import net.nemerosa.ontrack.graphql.support.intArgument
import net.nemerosa.ontrack.model.structure.*
import org.springframework.stereotype.Component

/**
 * Auto versioning information on a build or a branch
 */
@Component
class AutoVersioningGQLBuildFieldContributor(
    private val gqlTypeBuildAutoVersioning: GQLTypeBuildAutoVersioning,
    private val structureService: StructureService,
    private val autoVersioningConfigurationService: AutoVersioningConfigurationService,
) : GQLProjectEntityFieldContributor {
    override fun getFields(
        projectEntityClass: Class<out ProjectEntity>,
        projectEntityType: ProjectEntityType,
    ): List<GraphQLFieldDefinition>? {
        return if (projectEntityType == ProjectEntityType.BUILD || projectEntityType == ProjectEntityType.BRANCH) {
            listOf(
                GraphQLFieldDefinition.newFieldDefinition()
                    .name("autoVersioning")
                    .description("Auto versioning information for this build, in regards of a consumer branch, or of a dependency according to $ARG_REF_ROLE.")
                    .type(gqlTypeBuildAutoVersioning.typeRef)
                    .argument(
                        intArgument(
                            ARG_BUILD_ID,
                            "ID of the consumer build, or of the dependency according to $ARG_REF_ROLE. Takes precedence on the $ARG_BRANCH_ID argument, but at least one of each is required."
                        )
                    )
                    .argument(
                        intArgument(
                            ARG_BRANCH_ID,
                            "ID of the consumer branch, or of the dependency according to $ARG_REF_ROLE. Is overridden by the $ARG_BUILD_ID argument, but at least one of each is required."
                        )
                    )
                    .argument(
                        enumArgument<AutoVersioningRefRole>(
                            ARG_REF_ROLE,
                            "What $ARG_BUILD_ID or $ARG_BRANCH_ID refers to, ${AutoVersioningRefRole.CONSUMER} by default."
                        )
                    )
                    .argument(
                        GraphQLArgument.newArgument()
                            .name(ARG_DIRECTION)
                            .description("What $ARG_BUILD_ID or $ARG_BRANCH_ID refers to: ${AutoVersioningDirection.DOWN} for a consumer, ${AutoVersioningDirection.UP} for a dependency.")
                            .type(GraphQLTypeReference(AutoVersioningDirection::class.java.simpleName))
                            .deprecate("Removed in V7. Use refRole instead. See #2130")
                    )
                    .dataFetcher { env ->

                        // Local branch
                        val localBranch = if (projectEntityType == ProjectEntityType.BRANCH) {
                            env.getSource<Branch>()!!
                        } else {
                            env.getSource<Build>()!!.branch
                        }

                        // Reference branch
                        val refBuildId: Int? = env.getArgument(ARG_BUILD_ID)
                        val refBranchId: Int? = env.getArgument(ARG_BRANCH_ID)
                        val refBranch = if (refBuildId != null) {
                            structureService.getBuild(ID.of(refBuildId)).branch
                        } else if (refBranchId != null) {
                            structureService.getBranch(ID.of(refBranchId))
                        } else {
                            error("Either $ARG_BUILD_ID or $ARG_BRANCH_ID is required.")
                        }

                        // Getting the dependencies right
                        val refRole = env.getArgument<String>(ARG_REF_ROLE)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { AutoVersioningRefRole.valueOf(it) }
                            ?: env.getArgument<String>(ARG_DIRECTION)
                                ?.takeIf { it.isNotBlank() }
                                ?.let { AutoVersioningDirection.valueOf(it).refRole }
                            ?: AutoVersioningRefRole.CONSUMER
                        val dependency: Branch
                        val consumer: Branch
                        when (refRole) {
                            AutoVersioningRefRole.CONSUMER -> {
                                dependency = localBranch
                                consumer = refBranch
                            }

                            AutoVersioningRefRole.DEPENDENCY -> {
                                dependency = refBranch
                                consumer = localBranch
                            }
                        }

                        // Gets the AV config between the consumer branch and its dependency
                        val config =
                            autoVersioningConfigurationService.getAutoVersioningBetween(consumer, dependency)
                        // Returns the contextual object for this field
                        config?.run {
                            GQLTypeBuildAutoVersioning.Context(
                                consumer, dependency, this
                            )
                        }
                    }
                    .build(),
            )
        } else {
            null
        }
    }

    companion object {
        const val ARG_BUILD_ID = "buildId"
        const val ARG_BRANCH_ID = "branchId"
        const val ARG_REF_ROLE = "refRole"
        const val ARG_DIRECTION = "direction"
    }

}