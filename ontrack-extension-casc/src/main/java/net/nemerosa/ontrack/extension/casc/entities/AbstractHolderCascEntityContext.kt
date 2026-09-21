package net.nemerosa.ontrack.extension.casc.entities

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ProjectEntity

abstract class AbstractHolderCascEntityContext<T : SubCascEntityContext>(
    private val subContexts: List<T>,
    private val description: String,
) : AbstractCascEntityContext() {

    override fun run(entity: ProjectEntity, node: JsonNode, paths: List<String>) {
        runSubCascContexts(entity, node, paths, subContexts)
    }
}
