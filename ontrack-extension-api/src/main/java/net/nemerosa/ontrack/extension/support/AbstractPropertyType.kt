package net.nemerosa.ontrack.extension.support

import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.json.JsonParseException
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parseInto
import net.nemerosa.ontrack.model.exceptions.PropertyTypeStorageReadException
import net.nemerosa.ontrack.model.exceptions.PropertyValidationException
import net.nemerosa.ontrack.model.extension.ExtensionFeature
import net.nemerosa.ontrack.model.structure.Property
import net.nemerosa.ontrack.model.structure.PropertyType
import kotlin.reflect.KClass

abstract class AbstractPropertyType<T>(override val feature: ExtensionFeature) : PropertyType<T> {

    override fun of(value: T): Property<T> {
        return Property.of<T>(this, value)
    }

    override fun forStorage(value: T): JsonNode = value.asJson()

    protected fun validateNotBlank(value: String, message: String) {
        if (value.isBlank()) {
            throw PropertyValidationException(message)
        }
    }

    companion object {
        fun <V : Any> parse(node: JsonNode, type: KClass<V>): V {
            try {
                return node.parseInto(type)
            } catch (e: JsonParseException) {
                throw PropertyTypeStorageReadException(type, e)
            }
        }
    }
}
