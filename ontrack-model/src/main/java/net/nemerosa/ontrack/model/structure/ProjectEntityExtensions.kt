package net.nemerosa.ontrack.model.structure

import net.nemerosa.ontrack.model.exceptions.InputException
import kotlin.jvm.optionals.getOrNull

/**
 * Map of fields/values for the names of this entity
 */
val ProjectEntity.nameValues: Map<String, String>
    get() = when (this) {
        is Project -> mapOf(
            "project" to name
        )

        is Branch -> mapOf(
            "project" to project.name,
            "branch" to name
        )

        is Build -> mapOf(
            "project" to project.name,
            "branch" to branch.name,
            "build" to name
        )

        is ValidationStamp -> mapOf(
            "project" to project.name,
            "branch" to branch.name,
            "validation" to name
        )

        is PromotionLevel -> mapOf(
            "project" to project.name,
            "branch" to branch.name,
            "promotion" to name
        )

        is ValidationRun -> mapOf(
            "project" to project.name,
            "branch" to build.branch.name,
            "build" to build.name,
            "validation" to validationStamp.name,
            "run" to runOrder.toString()
        )

        is PromotionRun -> mapOf(
            "project" to project.name,
            "branch" to build.branch.name,
            "build" to build.name,
            "promotion" to promotionLevel.name
        )

        else -> throw IllegalStateException("Unknown project entity: $this")
    }

/**
 * List of names of this entity
 */
val ProjectEntityType.names: List<String>
    get() = when (this) {
        ProjectEntityType.PROJECT -> listOf(
            "project"
        )

        ProjectEntityType.BRANCH -> listOf(
            "project",
            "branch"
        )

        ProjectEntityType.BUILD -> listOf(
            "project",
            "branch",
            "build"
        )

        ProjectEntityType.VALIDATION_STAMP -> listOf(
            "project",
            "branch",
            "validation"
        )

        ProjectEntityType.PROMOTION_LEVEL -> listOf(
            "project",
            "branch",
            "promotion"
        )

        ProjectEntityType.VALIDATION_RUN -> listOf(
            "project",
            "branch",
            "build",
            "validation",
            "run"
        )

        ProjectEntityType.PROMOTION_RUN -> listOf(
            "project",
            "branch",
            "build",
            "promotion"
        )
    }

/**
 * Loading an entity using its names
 */
fun ProjectEntityType.loadByNames(structureService: StructureService, names: Map<String, String>): ProjectEntity? {
    return when (this) {
        ProjectEntityType.PROJECT -> structureService.findProjectByName(names.require("project")).orElse(null)
        ProjectEntityType.BRANCH -> structureService.findBranchByName(
            names.require("project"),
            names.require("branch")
        ).orElse(null)

        ProjectEntityType.BUILD -> structureService.findBuildByName(
            names.require("project"),
            names.require("branch"),
            names.require("build")
        ).orElse(null)

        ProjectEntityType.VALIDATION_STAMP -> structureService.findValidationStampByName(
            names.require("project"),
            names.require("branch"),
            names.require("validation")
        ).orElse(null)

        ProjectEntityType.PROMOTION_LEVEL -> structureService.findPromotionLevelByName(
            names.require("project"),
            names.require("branch"),
            names.require("promotion")
        ).orElse(null)

        ProjectEntityType.VALIDATION_RUN -> {
            // Gets the build
            val build = structureService.findBuildByName(
                names.require("project"),
                names.require("branch"),
                names.require("build")
            ).getOrNull() ?: return null
            // Gets the validation stamp
            val vs = structureService.findValidationStampByName(
                names.require("project"),
                names.require("branch"),
                names.require("validation")
            ).getOrNull() ?: return null
            // Run order
            val order = names.require("run").toInt()
            // Gets the runs
            // TODO Today, there is no query based on the order
            structureService.getValidationRunsForBuildAndValidationStamp(
                buildId = build.id,
                validationStampId = vs.id,
                offset = 0,
                count = 10
            ).find { it.runOrder == order }
        }

        ProjectEntityType.PROMOTION_RUN -> {
            // Gets the build
            val build = structureService.findBuildByName(
                names.require("project"),
                names.require("branch"),
                names.require("build")
            ).getOrNull() ?: return null
            // Gets the promotion level
            val pl = structureService.findPromotionLevelByName(
                names.require("project"),
                names.require("branch"),
                names.require("promotion")
            ).getOrNull() ?: return null
            // Gets the LAST promotion run
            structureService.getPromotionRunsForBuildAndPromotionLevel(build, pl).firstOrNull()
        }
    }
}

/**
 * Given a project entity, returns the list of all the parents
 */
fun ProjectEntity?.parents(): List<ProjectEntity> = if (this == null) {
    emptyList()
} else {
    listOf(this) + parent.parents()
}

private fun Map<String, String>.require(name: String) =
    get(name) ?: throw ProjectEntityNameMissingException(name)

class ProjectEntityNameMissingException(name: String) : InputException(
    """Name for "$name" is missing."""
)