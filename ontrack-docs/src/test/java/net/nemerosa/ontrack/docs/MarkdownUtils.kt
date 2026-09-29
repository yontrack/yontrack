package net.nemerosa.ontrack.docs

import net.nemerosa.ontrack.model.docs.FieldDocumentation
import net.nemerosa.ontrack.model.docs.getDocumentationExampleCode
import net.nemerosa.ontrack.model.docs.getFieldsDocumentation
import net.nemerosa.ontrack.model.json.schema.JsonSchemaListWrapper
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

fun StringBuilder.text(text: String) {
    appendLine(text)
}

fun StringBuilder.paragraph(text: String) {
    appendLine()
    appendLine(text)
    appendLine()
}

fun StringBuilder.title(title: String) {
    appendLine("# $title")
    appendLine()
}

fun StringBuilder.h2(title: String) {
    appendLine()
    appendLine("## $title")
    appendLine()
}

fun StringBuilder.tocItem(text: String, fileName: String, description: String? = null) {
    appendLine("* [$text]($fileName) ${description?.takeIf { it.isNotBlank() }?.let { " - _${it}_" } ?: ""}")
}

fun StringBuilder.listLinkItem(text: String, link: String) {
    appendLine("* [$text]($link)")
}

fun StringBuilder.definition(text: String) {
    appendLine(":    $text")
}

fun StringBuilder.code(code: String, language: String = "text") {
    appendLine("{% raw %}")
    appendLine("```${language}")
    appendLine(code)
    appendLine("```")
    appendLine("{% endraw %}")
}

fun StringBuilder.note(text: String) {
    appendLine("!!! note")
    appendLine("    ")
    appendLine(text.trimIndent().prependIndent("    "))
}

fun StringBuilder.table(vararg headers: String) {
    appendLine("| ${headers.joinToString(" | ")} |")
    appendLine("|${headers.joinToString("|") { "-".repeat(it.length + 2) }}|")
}

fun StringBuilder.tableRow(vararg cells: String) {
    appendLine("| ${cells.joinToString(" | ") { tableCell(it) }} |")
}

fun StringBuilder.example(type: KClass<*>) {
    val example = getDocumentationExampleCode(type)
    if (!example.isNullOrBlank()) {
        h2("Example")
        code(example.trimIndent())
    }
}

fun StringBuilder.configuration(type: KClass<*>, title: String? = "Configuration", description: String? = null) {
    if (isScalarClass(type)) {
        if (!title.isNullOrBlank()) {
            h2(title)
        }
        val s = StringBuilder(type.java.simpleName)
        if (!description.isNullOrBlank()) {
            s.append(" - ").append("_${description}_")
        }
        text(s.toString())
    } else {
        val listWrapper = type.findAnnotation<JsonSchemaListWrapper>()
        if (listWrapper != null) {
            if (!title.isNullOrBlank()) {
                h2(title)
            }
            wrappedList(type, listWrapper.listProperty)
        } else {
            fields(type, title)
        }
    }
}

fun StringBuilder.wrappedList(type: KClass<*>, propertyName: String) {
    val property = type.memberProperties.find { it.name == propertyName }
        ?: error("Cannot find property $propertyName in $type")

    // We expect the property to be a List<T>. If not, raise an error.
    val returnType = property.returnType
    val classifier = returnType.classifier
    require(classifier is KClass<*> && classifier.qualifiedName == List::class.qualifiedName) {
        "Property $propertyName in $type is not a List"
    }

    // Extract the generic argument T from List<T>
    val argType = returnType.arguments.firstOrNull()?.type
        ?: error("Cannot determine the list element type for property $propertyName in $type")
    val argClassifier = argType.classifier
    require(argClassifier is KClass<*>) {
        "List element type for property $propertyName in $type is not a class"
    }

    // Call fields on the element type
    paragraph("List of elements of type:")
    fields(argClassifier, null)
}

fun StringBuilder.fields(type: KClass<*>, title: String? = "Configuration") {
    val fields = getFieldsDocumentation(type)
    if (!title.isNullOrBlank()) {
        h2(title)
    }
    writeFields(fields, 1)
}

fun StringBuilder.writeFields(
    fields: List<FieldDocumentation>,
    level: Int = 1,
) {
    fields
        .sortedBy { it.name }
        .forEach { (name, description, type, required, subfields, aliases) ->
            append(" ".repeat(4 * (level - 1))).append("*").append(" **").append(name).append("** - ")
                .append(type)
                .append(" - ")
                .append(if (required) "required" else "optional")
                .appendDescription(description)
                .appendAliases(aliases)
                .append("\n")
                .append("\n")
            if (subfields.isNotEmpty()) {
                writeFields(subfields, level + 1)
            }
        }
}

private fun StringBuilder.appendDescription(description: String?): StringBuilder =
    if (!description.isNullOrBlank()) {
        append(" - _${description.trimIndent()}_")
    } else {
        this
    }

private fun StringBuilder.appendAliases(aliases: List<String>): StringBuilder =
    if (aliases.isNotEmpty()) {
        append(" (aliases: ${aliases.joinToString(", ") { "`$it`" }})")
    } else {
        this
    }

private fun tableCell(text: String) = text.replace("\n", "<br/>")
