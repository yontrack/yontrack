package net.nemerosa.ontrack.extension.casc.entities

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.structure.ProjectEntity

abstract class AbstractCascEntityContext : CascEntityContext {

    /**
     * Known list of fields to handle
     */
    protected fun run(
        entity: ProjectEntity,
        node: JsonNode,
        paths: List<String>,
        vararg mapping: Pair<String, CascEntityContext>,
    ) {
        run(entity, node, paths, mapping.toMap())
    }

    /**
     * Sub contexts registred for fields
     */
    protected fun runSubCascContexts(
        entity: ProjectEntity,
        node: JsonNode,
        paths: List<String>,
        subCascContexts: List<SubCascEntityContext>,
    ) {
        run(entity, node, paths, subCascContexts.associateBy { it.field })
    }

    /**
     * Known list of fields to handle
     */
    protected fun run(
        entity: ProjectEntity,
        node: JsonNode,
        paths: List<String>,
        mapping: Map<String, CascEntityContext>,
    ) {

        class FieldContext(
            val name: String,
            val value: JsonNode,
            val context: CascEntityContext,
        )

        // We need to respect the order of contexts set by [CascEntityContext.priority]
        // Map each JSON field to its corresponding context and order them by decreasing priority
        val fieldContexts = mutableListOf<FieldContext>()
        node.properties().forEach { (name, value) ->
            val context = mapping[name]
            if (context != null) {
                fieldContexts += FieldContext(
                    name = name,
                    value = value,
                    context = context,
                )
            } else {
                error("No CasC context is defined for ${path(paths + name)}")
            }
        }

        // Order fields by decreasing priority
        fieldContexts.sortByDescending { it.context.priority }

        // Processes the fields in order
        fieldContexts.forEach { f ->
            f.context.run(entity, f.value, paths + f.name)
        }
    }

    protected fun path(paths: List<String>) =
        paths.joinToString("/")

}