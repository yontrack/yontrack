package net.nemerosa.ontrack.model.docs

import com.fasterxml.jackson.annotation.JsonAlias
import tools.jackson.databind.JsonNode
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import net.nemerosa.ontrack.model.annotations.getPropertyName
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.*


inline fun <reified A : Annotation> findAnnotation(documentationClass: KClass<*>, property: KProperty<*>): A? =
    property.findAnnotation<A>()
        ?: property.getter.findAnnotation<A>()
        ?: documentationClass.primaryConstructor?.parameters?.find { it.name == property.name }
            ?.findAnnotation<A>()

inline fun <reified A : Annotation> hasAnnotation(documentationClass: KClass<*>, property: KProperty<*>): Boolean =
    findAnnotation<A>(documentationClass, property) != null

fun getFieldsForDocumentationClass(documentationClass: KClass<*>): List<FieldDocumentation> {
    val fields = mutableListOf<FieldDocumentation>()
    documentationClass.memberProperties.forEach { property ->
        if (property.visibility == KVisibility.PUBLIC && !hasAnnotation<DocumentationIgnore>(documentationClass, property)) {

            val name = getPropertyName(property)
            val description =
                findAnnotation<DocumentationType>(documentationClass, property)
                    ?.description?.takeIf { it.isNotBlank() }
                    ?: getPropertyDescription(property)

            // Type
            val fieldType = getFieldType(documentationClass, property)

            // Required?
            val required = !property.returnType.isMarkedNullable

            // Aliases
            val jsonAlias = findAnnotation<JsonAlias>(documentationClass, property)
            val aliases = jsonAlias?.value?.toList() ?: emptyList()

            // Subfields
            val propertyType = property.returnType
            val subfields = if (propertyType.classifier == List::class && hasAnnotation<DocumentationList>(documentationClass, property)) {
                val itemType = property.returnType.arguments.firstOrNull()?.type?.classifier
                if (itemType != null && itemType is KClass<*>) {
                    getFieldsDocumentation(itemType)
                } else {
                    emptyList()
                }
            } else if (hasAnnotation<DocumentationField>(documentationClass, property)) {
                val actualPropertyType = propertyType.classifier
                if (actualPropertyType != null && actualPropertyType is KClass<*>) {
                    getFieldsDocumentation(actualPropertyType)
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }

            fields += FieldDocumentation(
                name = name,
                description = description,
                type = fieldType,
                required = required,
                subfields = subfields,
                aliases = aliases
            )
        }
    }
    // Super class
    if (documentationClass.hasAnnotation<DocumentationUseSuper>()) {
        val superClass = documentationClass.supertypes.firstOrNull()?.classifier
        if (superClass != null && superClass is KClass<*>) {
            fields += getFieldsForDocumentationClass(superClass)
        }
    }
    // OK
    return fields
}

fun getFieldsDocumentation(type: KClass<*>, section: String = "", required: Boolean = true): List<FieldDocumentation> {
    val documentationAnnotation = type.findAnnotations<Documentation>().firstOrNull { it.section == section }
    if (documentationAnnotation == null && !required) return emptyList()
    val documentationClass = documentationAnnotation?.value ?: type
    return getFieldsForDocumentationClass(documentationClass)
}

fun getFieldType(documentationClass: KClass<*>, property: KProperty1<out Any, *>): String {
    // Explicit annotation
    val typeAnnotation = findAnnotation<DocumentationType>(documentationClass, property)
    if (typeAnnotation != null) {
        return typeAnnotation.value
    }
    val classifier = property.returnType.classifier
    return when (classifier) {
        String::class -> "String"
        Int::class -> "Int"
        Long::class -> "Long"
        Boolean::class -> "Boolean"
        List::class -> "List"
        JsonNode::class -> "JSON"
        LocalDateTime::class -> "LocalDateTime"
        Duration::class -> "String"
        else -> if (classifier is KClass<*>) {
            when {
                classifier.isSubclassOf(Enum::class) ->
                    (classifier.java.enumConstants ?: error("Not an enum")).joinToString(", ")

                hasAnnotation<DocumentationField>(documentationClass, property) -> "Object"
                else -> error("Classifier $classifier for property $property is not supported")
            }
        } else {
            error("Classifier $classifier for property $property is not supported")
        }
    }
}

fun getDocumentationExampleCode(
    type: KClass<*>,
    trimIndent: Boolean = true,
): String? =
    type.findAnnotation<DocumentationExampleCode>()
        ?.value
        ?.run {
            if (trimIndent) {
                trimIndent().trim()
            } else {
                this
            }
        }

data class FieldDocumentation(
    val name: String,
    val description: String?,
    val type: String,
    val required: Boolean,
    val subfields: List<FieldDocumentation>,
    val aliases: List<String>,
)
