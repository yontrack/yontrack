package net.nemerosa.ontrack.boot.ui

import net.nemerosa.ontrack.model.support.Configuration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import kotlin.test.assertEquals

/**
 * A [Configuration] carries credentials, and the ones handed out by a [net.nemerosa.ontrack.model.support.ConfigurationService]
 * are *decrypted*. The GraphQL path strips them twice - `obfuscate()` in
 * `GQLRootQueryConfigurations` and the field filtering in `GQLScalarJSON` - but a REST
 * controller returning the object does neither: Jackson writes the token out in clear, to
 * whoever is authenticated.
 *
 * That is what the legacy per-extension REST controllers did (#1742). They are gone, and this
 * test is what stops the shape from coming back: no MVC handler may declare a [Configuration]
 * in its return type.
 *
 * Two limits are worth knowing before trusting a green run. The check is *static*: a handler
 * declaring `Any`, `JsonNode` or `ResponseEntity<*>` and returning a configuration at runtime
 * passes it. And it only sees the controllers on this module's test runtime classpath - every
 * extension in `settings.gradle.kts` is currently a `runtimeOnly` of `ontrack-ui`, but an
 * extension added to the build and not to that list would escape the guard with no failure to
 * say so.
 */
class ConfigurationRestExposureIT : AbstractWebTestSupport() {

    @Autowired
    private lateinit var requestMappingHandlerMapping: RequestMappingHandlerMapping

    @Test
    fun `No REST endpoint returns a configuration`() {
        val offenders = requestMappingHandlerMapping.handlerMethods
            .filterValues { it.method.genericReturnType.mentionsConfiguration() }
            .map { (mapping, method) -> "$mapping -> ${method.method.declaringClass.simpleName}.${method.method.name}" }
            .sorted()
        assertEquals(
            emptyList(),
            offenders,
            "REST endpoints returning a decrypted configuration - use GraphQL `configurations` instead",
        )
    }

    /**
     * True as soon as a [Configuration] appears anywhere the type is written out, so that a
     * `List<X>` or a `ResponseEntity<List<X>>` is caught as surely as a bare `X`.
     */
    private fun Type.mentionsConfiguration(): Boolean = when (this) {
        is Class<*> -> Configuration::class.java.isAssignableFrom(this)
        is ParameterizedType -> rawType.mentionsConfiguration() || actualTypeArguments.any { it.mentionsConfiguration() }
        is GenericArrayType -> genericComponentType.mentionsConfiguration()
        is WildcardType -> upperBounds.any { it.mentionsConfiguration() }
        is TypeVariable<*> -> bounds.any { it.mentionsConfiguration() }
        else -> false
    }
}
