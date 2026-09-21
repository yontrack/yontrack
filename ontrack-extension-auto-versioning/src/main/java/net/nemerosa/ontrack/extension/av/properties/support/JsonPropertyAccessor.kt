package net.nemerosa.ontrack.extension.av.properties.support

import tools.jackson.databind.node.*
import org.springframework.expression.AccessException
import org.springframework.expression.EvaluationContext
import org.springframework.expression.PropertyAccessor
import org.springframework.expression.TypedValue

class JsonPropertyAccessor : PropertyAccessor {

    override fun getSpecificTargetClasses(): Array<Class<*>> =
        arrayOf(ObjectNode::class.java)

    override fun canRead(context: EvaluationContext, target: Any?, name: String): Boolean {
        return target is ObjectNode && target.has(name)
    }

    override fun canWrite(context: EvaluationContext, target: Any?, name: String): Boolean {
        return target is ObjectNode
    }

    override fun write(context: EvaluationContext, target: Any?, name: String, newValue: Any?) {
        if (target is ObjectNode) {
            when (newValue) {
                is Int -> target.set(name, IntNode(newValue)) as ObjectNode
                is Long -> target.set(name, LongNode(newValue)) as ObjectNode
                is Boolean -> target.set(name, BooleanNode.valueOf(newValue)) as ObjectNode
                is String -> target.set(name, StringNode(newValue)) as ObjectNode
                else -> throw AccessException("Cannot set value for field $name on $target")
            }
        } else {
            throw AccessException("Cannot set value for field $name on $target")
        }
    }

    override fun read(context: EvaluationContext, target: Any?, name: String): TypedValue {
        return if (target is ObjectNode && target.has(name)) {
            val value: Any? = when (val node = target.get(name)) {
                is IntNode -> node.intValue()
                is LongNode -> node.longValue()
                is BooleanNode -> node.booleanValue()
                is StringNode -> node.stringValue()
                is NullNode -> null
                else -> node
            }
            value?.let { TypedValue(it) } ?: TypedValue.NULL
        } else {
            throw AccessException("Cannot get field $name on $target")
        }
    }

}