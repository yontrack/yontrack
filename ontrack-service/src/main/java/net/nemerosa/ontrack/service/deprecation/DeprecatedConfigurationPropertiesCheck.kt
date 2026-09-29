package net.nemerosa.ontrack.service.deprecation

import net.nemerosa.ontrack.model.deprecation.DeprecatedConfigurationPropertiesProvider
import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Reports, once at startup, every deprecated configuration property which is set,
 * whatever its source and spelling (YAML, system property, environment variable).
 */
@Component
class DeprecatedConfigurationPropertiesCheck(
    private val environment: Environment,
    private val providers: List<DeprecatedConfigurationPropertiesProvider>,
    private val deprecationService: DeprecationService,
) {

    @EventListener(ApplicationReadyEvent::class)
    fun check() {
        val binder = Binder.get(environment)
        providers
            .flatMap { it.deprecatedConfigurationProperties }
            .filter { binder.bind(it.name, Bindable.of(String::class.java)).isBound }
            .forEach { property ->
                deprecationService.deprecatedUsage(
                    surface = DeprecationSurface.CONFIG,
                    item = property.name,
                    message = property.message,
                )
            }
    }
}
