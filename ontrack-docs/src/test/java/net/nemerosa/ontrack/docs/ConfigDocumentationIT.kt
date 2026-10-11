package net.nemerosa.ontrack.docs

import net.nemerosa.ontrack.common.camelCaseToEnvironmentName
import net.nemerosa.ontrack.common.camelCaseToKebabCase
import net.nemerosa.ontrack.model.annotations.getAPITypeDescription
import net.nemerosa.ontrack.model.annotations.getAPITypeName
import net.nemerosa.ontrack.model.annotations.getPropertyDescription
import net.nemerosa.ontrack.model.docs.DocumentationIgnore
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.convert.DataSizeUnit
import org.springframework.boot.convert.DurationUnit
import org.springframework.core.io.ClassPathResource
import org.springframework.util.unit.DataSize
import org.springframework.util.unit.DataUnit
import java.time.Duration
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
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
                s.tocItem(SPRING_BOOT_TITLE, fileName = "$SPRING_BOOT_FILE.md")
            }

            configurations.forEach { configuration ->
                generateConfiguration(this, configuration)
            }

            generateSpringBootConfiguration(this)

        }
    }

    /**
     * The Spring Boot properties which Yontrack sets in its `config/application.yml`, with the value
     * it gives them: a property listed here and missing from the file fails the generation.
     */
    private fun generateSpringBootConfiguration(directoryContext: DocGenDirectoryContext) {
        val applicationProperties = YamlPropertiesFactoryBean().run {
            setResources(ClassPathResource("config/application.yml"))
            `object` ?: error("Cannot read config/application.yml")
        }
        directoryContext.writeFile(
            fileId = SPRING_BOOT_FILE,
            title = SPRING_BOOT_TITLE,
        ) { s ->
            s.paragraph("Spring Boot properties which Yontrack sets to a value of its own. They can be overridden like any Spring Boot property.")
            s.table("Name", "Environment", "Description", "Default value", "Notes")
            springBootProperties.toSortedMap().forEach { (name, description) ->
                val value = applicationProperties.getProperty(name)
                    ?: error("$name is not set in config/application.yml")
                writeProperty(
                    s = s,
                    propertyName = name,
                    // Spring Boot binds an environment variable without the dashes of the property name
                    envName = name.replace(".", "_").replace("-", "").uppercase(),
                    description = description,
                    defaultValue = value,
                    deprecatedReason = "",
                )
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
            writeProperties(s, directoryContext, configuration, defaults(configuration))

        }
    }

    /**
     * The default values of a configuration: a new instance of its class when it has a constructor
     * without arguments, the bean itself otherwise.
     *
     * The bean is bound to the environment of the tests — the `dev` profile and the properties of
     * the integration test stack, its storage credentials among them — which are not defaults.
     */
    private fun defaults(configuration: Any): Any =
        try {
            configuration::class.createInstance()
        } catch (_: IllegalArgumentException) {
            configuration
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
                } else if (child is DataSize) {
                    writeDataSizeProperty(
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

    /**
     * A data size, in the unit of its [DataSizeUnit] when it has one, in bytes otherwise.
     */
    private fun writeDataSizeProperty(
        s: StringBuilder,
        propertyName: String,
        envName: String,
        member: KProperty1<out Any, *>,
        current: DataSize,
        deprecatedReason: String,
    ) {
        val description = getPropertyDescription(member)

        // The annotation targets the field: Kotlin puts it on the backing field, not on the property
        val unitAnnotation = member.findAnnotation<DataSizeUnit>()
            ?: member.javaField?.getAnnotation(DataSizeUnit::class.java)
        val defaultValue = when (val unit = unitAnnotation?.value) {
            null, DataUnit.BYTES -> "${current.toBytes()} (BYTES)"
            DataUnit.KILOBYTES -> "${current.toKilobytes()} (${unit.name})"
            DataUnit.MEGABYTES -> "${current.toMegabytes()} (${unit.name})"
            DataUnit.GIGABYTES -> "${current.toGigabytes()} (${unit.name})"
            DataUnit.TERABYTES -> "${current.toTerabytes()} (${unit.name})"
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

    companion object {
        private const val SPRING_BOOT_FILE = "spring-boot"
        private const val SPRING_BOOT_TITLE = "Spring Boot configuration"

        /**
         * Description of the Spring Boot properties which Yontrack sets, indexed by name.
         */
        private val springBootProperties = mapOf(
            "spring.lifecycle.timeout-per-shutdown-phase" to "When Yontrack stops - on a `SIGTERM`, during a rolling upgrade - time given to each phase of its graceful shutdown: the web server completing the requests in progress, the queue listeners completing the message in hand (see `ontrack.config.rabbitmq.listener-shutdown-timeout`). It fits in the termination grace period of 60 seconds of the Helm chart, after its `preStop` hook.",
        )
    }

}
