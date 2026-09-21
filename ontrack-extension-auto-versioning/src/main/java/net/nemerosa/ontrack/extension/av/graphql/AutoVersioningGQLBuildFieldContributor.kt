package net.nemerosa.ontrack.extension.av.graphql

import graphql.schema.GraphQLFieldDefinition
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
                    .description("Auto versioning information for this build, in regards of a parent branch")
                    .type(gqlTypeBuildAutoVersioning.typeRef)
                    .argument(
                        intArgument(
                            ARG_BUILD_ID,
                            "ID of the parent build. Takes precedence on the $ARG_BRANCH_ID argument, but at least one of each is required."
                        )
                    )
                    .argument(
                        intArgument(
                            ARG_BRANCH_ID,
                            "ID of the parent branch. Is overridden by the $ARG_BUILD_ID argument, but at least one of each is required."
                        )
                    )
                    .argument(
                        enumArgument<AutoVersioningDirection>(
                            ARG_DIRECTION,
                            "Downstream or upstream dependencies (down by default)."
                        )
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
                        val direction = env.getArgument<String>(ARG_DIRECTION)
                            ?.takeIf { it.isNotBlank() }
                            ?.let { AutoVersioningDirection.valueOf(it) }
                            ?: AutoVersioningDirection.DOWN
                        val dependency: Branch
                        val parent: Branch
                        when (direction) {
                            AutoVersioningDirection.DOWN -> {
                                dependency = localBranch
                                parent = refBranch
                            }

                            AutoVersioningDirection.UP -> {
                                dependency = refBranch
                                parent = localBranch
                            }
                        }

                        // Gets the AV config between the parent branch and its dependency
                        val config =
                            autoVersioningConfigurationService.getAutoVersioningBetween(parent, dependency)
                        // Returns the contextual object for this field
                        config?.run {
                            GQLTypeBuildAutoVersioning.Context(
                                parent, dependency, this
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
        const val ARG_DIRECTION = "direction"
    }

}