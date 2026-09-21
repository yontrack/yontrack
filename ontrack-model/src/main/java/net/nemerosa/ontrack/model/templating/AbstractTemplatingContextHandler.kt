package net.nemerosa.ontrack.model.templating

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.parseInto
import kotlin.reflect.KClass

abstract class AbstractTemplatingContextHandler<T : TemplatingContext>(
    private val dataClass: KClass<T>,
) : TemplatingContextHandler<T> {

    override fun deserialize(data: JsonNode): T =
        data.parseInto(dataClass)

}