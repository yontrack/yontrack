package net.nemerosa.ontrack.extension.github.ingestion.processing.buildid

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.github.ingestion.processing.events.WorkflowRun
import net.nemerosa.ontrack.extension.github.ingestion.processing.model.normalizeName
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build

interface BuildIdStrategy {

    val id: String

    fun findBuild(branch: Branch, workflowRun: WorkflowRun, config: JsonNode): Build?

    fun setupBuild(build: Build, workflowRun: WorkflowRun, config: JsonNode)

    fun getBuildName(workflowRun: WorkflowRun, config: JsonNode): String = normalizeName(
        "${workflowRun.name}-${workflowRun.runNumber}"
    )

    /**
     * Checking if a build can be created using this strategy.
     */
    fun canCreateBuild(branch: Branch, workflowRun: WorkflowRun, config: JsonNode): Boolean

}