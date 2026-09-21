package net.nemerosa.ontrack.extension.github.ingestion.processing.buildid

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.extension.git.property.GitCommitProperty
import net.nemerosa.ontrack.extension.git.property.GitCommitPropertyType
import net.nemerosa.ontrack.extension.github.ingestion.processing.events.WorkflowRun
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PropertyService
import net.nemerosa.ontrack.model.structure.StructureService
import org.springframework.stereotype.Component

@Component
class CommitBuildIdStrategy(
    private val propertyService: PropertyService,
    private val structureService: StructureService,
) : AbstractBuildIdStrategy(ID) {

    companion object {
        const val ID = "commit"
    }

    override fun canCreateBuild(branch: Branch, workflowRun: WorkflowRun, config: JsonNode): Boolean =
        workflowRun.headSha.isNotBlank()

    override fun findBuild(branch: Branch, workflowRun: WorkflowRun, config: JsonNode): Build? =
        propertyService.findBuildByBranchAndSearchkey(
            branch.id,
            GitCommitPropertyType::class.java,
            workflowRun.headSha,
        )?.let { id ->
            structureService.getBuild(id)
        }

    override fun setupBuild(build: Build, workflowRun: WorkflowRun, config: JsonNode) {
        if (!propertyService.hasProperty(build, GitCommitPropertyType::class.java)) {
            propertyService.editProperty(
                build,
                GitCommitPropertyType::class.java,
                GitCommitProperty(workflowRun.headSha)
            )
        }
    }
}