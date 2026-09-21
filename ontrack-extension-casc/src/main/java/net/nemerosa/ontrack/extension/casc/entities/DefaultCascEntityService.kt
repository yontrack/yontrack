package net.nemerosa.ontrack.extension.casc.entities

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.security.ProjectConfig
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.structure.ProjectEntity
import net.nemerosa.ontrack.model.structure.ProjectEntityType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class DefaultCascEntityService(
    private val securityService: SecurityService,
    private val projectContext: CascEntityProjectContext,
    private val branchContext: CascEntityBranchContext,
) : CascEntityService {

    override fun apply(entity: ProjectEntity, node: JsonNode) {
        securityService.checkProjectFunction(entity, ProjectConfig::class.java)
        // Gets the root context for this entity type
        val rootContext = when (entity.projectEntityType) {
            ProjectEntityType.PROJECT -> projectContext
            ProjectEntityType.BRANCH -> branchContext
            else -> error("Casc for ${entity.projectEntityType} is not supported.")
        }
        // Applies the configuration
        rootContext.run(entity, node, emptyList())
    }

}