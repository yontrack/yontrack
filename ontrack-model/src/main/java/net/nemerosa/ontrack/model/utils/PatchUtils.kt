package net.nemerosa.ontrack.model.utils

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.common.mergeList
import net.nemerosa.ontrack.json.parse
import kotlin.reflect.KProperty0

inline fun <reified E : Any> patchList(
    changes: JsonNode,
    property: KProperty0<List<E>>,
    noinline idFn: (E) -> Any,
) =
    if (changes.has(property.name)) {
        val customChanges = changes.path(property.name)
            .values().map { it.parse<E>() }
        mergeList(property.get(), customChanges, idFn) { e, _ -> e }
    } else {
        property.get()
    }
