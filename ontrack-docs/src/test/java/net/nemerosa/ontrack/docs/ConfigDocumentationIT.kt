package net.nemerosa.ontrack.docs

import net.nemerosa.ontrack.common.camelCaseToEnvironmentName
import net.nemerosa.ontrack.common.camelCaseToKebabCase
import net.nemerosa.ontrack.model.annotations.getAPITypeDescription
import net.nemerosa.ontrack.model.annotations.getAPITypeName
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import net.nemerosa.ontrack.model.docs.DocumentationIgnore
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DurationUnit
import java.time.Duration
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmName

class ConfigDocumentationIT : AbstractDocGenIT() {

    @Test
    fun `Configuration properties`() {
        val configurations = findAllBeansAnnotatedWith(ConfigurationProperties::class)
            .filter {
                it::class.jvmName.startsWith("net.nemerosa.ontrack") ||
                        it::class.jvmName.startsWith("com.yontrack")
            }
            .filterNot {
                it::class.hasAnnotation<DocumentationIgnore>()
            }

        docGenSupport.inDirectory("configurations") {

            writeFile(
                fileName = "index",
            ) { s ->
                s.title("Configuration properties")
                for (configuration in configurations.sortedBy { getAPITypeName(it::class) }) {
                    val id = configuration::class.jvmName
                    val name = getAPITypeName(configuration::class)
                    s.tocItem(name, fileName = "${id}.md")
                }
            }

            configurations.forEach { configuration ->
                generateConfiguration(this, configuration)
            }

        }
    }

    private fun generateConfiguration(
        directoryContext: DocGenDirectoryContext,
        configuration: Any
    ) {
        println("Generation docs for configuration $configuration")

        val id = configuration::class.jvmName
        val name = getAPITypeName(configuration::class)

        directoryContext.writeFile(
            fileId = id,
            title = name,
        ) { s ->
            // Description
            val description = getAPITypeDescription(configuration::class)
            if (!description.isNullOrBlank()) {
                s.paragraph(description)
            }

            // Fields
            s.table("Name", "Environment", "Description", "Default value", "Notes")
            writeProperties(s, directoryContext, configuration, configuration)

        }
    }

    private fun writeProperties(
        s: StringBuilder,
        directoryContext: DocGenDirectoryContext,
        configuration: Any,
        current: Any,
        prefix: String = "",
    ) {
        val classPrefix = configuration::class.findAnnotation<ConfigurationProperties>()
            ?.prefix
            ?: error("$configuration is not annotated with ConfigurationProperties")
        val properties = current::class.memberProperties.filter {
            it.visibility == KVisibility.PUBLIC
        }

        val deprecatedReason = current::class.findAnnotation<Deprecated>()?.message
            ?.let { "Deprecated: $it" }
            ?: ""

        for (member in properties) {

            val baseName = if (prefix.isBlank()) {
                member.name
            } else {
                "$prefix.${member.name}"
            }
            val qualifiedName = "$classPrefix.$baseName"

            val propertyName = qualifiedName.camelCaseToKebabCase()
            val envName = qualifiedName.camelCaseToEnvironmentName()

            if (isScalarProperty(member)) {
                val description = getPropertyDescription(member)

                val defaultValue = member.call(current)
                    ?.toString()
                    ?: ""

                val memberDeprecatedReason = member.findAnnotation<Deprecated>()?.message
                    ?.let { "Deprecated: $it" }
                    ?: deprecatedReason

                writeProperty(s, propertyName, envName, description, defaultValue, memberDeprecatedReason)
            } else {
                val child = try {
                    member.call(current)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    null
                }
                    ?: error("Cannot get documentation for undefined children: $member [configuration = $configuration, prefix = $prefix")

                if (child is Map<*, *>) {
                    val kType = member.returnType       // KType for Map<String, MyClass>
                    // Extract type arguments
                    val (keyArg, valueArg) = kType.arguments
                    val keyType = keyArg.type              // KType for String
                    val valueType = valueArg.type          // KType for MyClass

                    val keyClass = keyType?.classifier
                    if (keyClass == String::class) {
                        val valueClass = valueType?.classifier as? KClass<*>
                        val instance = valueClass?.createInstance()
                            ?: error("Cannot generate documentation because cannot instantiate class: $member [configuration = $configuration, prefix = $prefix")

                        val description = getPropertyDescription(member)

                        s.tableRow(
                            "`$propertyName.<.*>`",
                            "`-`",
                            description,
                            "_Empty_",
                            deprecatedReason,
                        )

                        writeProperties(
                            s = s,
                            directoryContext = directoryContext,
                            configuration = configuration,
                            current = instance,
                            prefix = "$prefix.${member.name}.<*>",
                        )
                    } else {
                        error("Cannot generate documentation for maps where the key is not a String: $member")
                    }
                } else if (child is List<*>) {
                    val kType = member.returnType       // KType for List<MyClass>
                    val (valueArg) = kType.arguments
                    val valueType = valueArg.type          // KType for MyClass
                    val valueClass = valueType?.classifier as? KClass<*>
                    val instance = valueClass?.createInstance()
                        ?: error("Cannot generate documentation because cannot instantiate class: $member [configuration = $configuration, prefix = $prefix")

                    if (isScalarClass(valueClass)) {
                        val description = getPropertyDescription(member)
                        writeProperty(
                            s = s,
                            propertyName = "$propertyName.<0>",
                            envName = "${envName}_<0>",
                            description = description,
                            defaultValue = "_Empty list_",
                            deprecatedReason = deprecatedReason,
                        )
                    } else {
                        writeProperties(
                            s = s,
                            directoryContext = directoryContext,
                            configuration = configuration,
                            current = instance,
                            prefix = "$prefix.${member.name}.<*>",
                        )
                    }
                } else if (child is Enum<*>) {
                    writeEnumProperty(
                        s = s,
                        propertyName = propertyName,
                        envName = envName,
                        member = member,
                        current = child,
                        deprecatedReason = deprecatedReason,
                    )
                } else if (child is Duration) {
                    writeDurationProperty(
                        s = s,
                        propertyName = propertyName,
                        envName = envName,
                        member = member,
                        current = child,
                        deprecatedReason = deprecatedReason,
                    )
                } else {
                    writeProperties(
                        s = s,
                        directoryContext = directoryContext,
                        configuration = configuration,
                        current = child,
                        prefix = if (prefix.isBlank()) {
                            member.name
                        } else {
                            "$prefix.${member.name}"
                        },
                    )
                }
            }
        }
    }

    private fun writeDurationProperty(
        s: StringBuilder,
        propertyName: String,
        envName: String,
        member: KProperty1<out Any, *>,
        current: Duration,
        deprecatedReason: String,
    ) {
        val description = getPropertyDescription(member)

        var defaultValue = current.toString()
        val durationUnit = member.findAnnotation<DurationUnit>()
        if (durationUnit != null) {
            val unit = durationUnit.value
            defaultValue += " (${unit.name})"
        }

        writeProperty(s, propertyName, envName, description, defaultValue, deprecatedReason)
    }

    private fun writeEnumProperty(
        s: StringBuilder,
        propertyName: String,
        envName: String,
        member: KProperty1<out Any, *>,
        current: Enum<*>,
        deprecatedReason: String,
    ) {
        val description = getPropertyDescription(member)

        val defaultValue = current.name

        writeProperty(s, propertyName, envName, description, defaultValue, deprecatedReason)
    }

    private fun writeProperty(
        s: StringBuilder,
        propertyName: String,
        envName: String,
        description: String,
        defaultValue: String,
        deprecatedReason: String,
    ) {
        s.tableRow(
            "`${propertyName}`",
            "`${envName}`",
            description,
            defaultValue,
            deprecatedReason
        )
    }

    fun isScalarProperty(prop: KProperty<*>): Boolean {
        val classifier = prop.returnType.classifier
        return classifier is KClass<*> && isScalarClass(classifier)
    }

}