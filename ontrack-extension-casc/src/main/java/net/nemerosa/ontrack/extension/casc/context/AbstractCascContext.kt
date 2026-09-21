package net.nemerosa.ontrack.extension.casc.context

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.extension.casc.CascContext
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.annotations.getPropertyName
import kotlin.reflect.KProperty

abstract class AbstractCascContext : CascContext {

    /**
     * Known list of fields to handle
     */
    protected fun JsonNode.run(
        paths: List<String>,
        vararg mapping: Pair<String, CascContext>,
    ) {
        run(paths, mapping.toMap())
    }

    /**
     * Sub contexts registred for fields
     */
    protected fun JsonNode.runSubCascContexts(
        paths: List<String>,
        subCascContexts: List<SubCascContext>,
    ) {
        run(paths, subCascContexts.associateBy { it.field })
    }

    /**
     * Checked parsing of a JSON array
     */
    protected inline fun <reified T : Any> JsonNode.mapEachTo(paths: List<String>) =
        mapIndexed { index, child ->
            try {
                child.parse<T>()
            } catch (ex: JsonParseException) {
                throw IllegalStateException(
                    "Cannot parse into ${T::class.qualifiedName}: ${path(paths + index.toString())}",
                    ex
                )
            }
        }

    /**
     * Known list of fields to handle
     */
    protected fun JsonNode.run(
        paths: List<String>,
        mapping: Map<String, CascContext>,
    ) {

        class FieldContext(
            val name: String,
            val value: JsonNode,
            val context: CascContext,
        )

        // We need to respect the order of contexts set by [CascContext.priority]
        // Map each JSON field to its corresponding context and order them by decreasing priority
        val fieldContexts = mutableListOf<FieldContext>()
        properties().forEach { (name, value) ->
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
            f.context.run(f.value, paths + f.name)
        }
    }

    protected fun path(paths: List<String>) =
        paths.joinToString("/")

    protected class SubContext(
        val node: JsonNode,
        val paths: List<String>,
    ) {
        fun runWith(context: CascContext) {
            context.run(node, paths)
        }
    }

    protected infix fun SubContext?.run(context: CascContext) {
        this?.runWith(context)
    }

    /**
     * Adapting an input JSON for missing non-required variables before parsing
     */
    protected fun JsonNode.ifMissing(vararg mappings: Pair<KProperty<*>, Any>): JsonNode {
        mappings.forEach { (property, value) ->
            val name = getPropertyName(property)
            if (this is ObjectNode && !has(name)) {
                set(name, value.asJson())
            }
        }
        return this
    }
}