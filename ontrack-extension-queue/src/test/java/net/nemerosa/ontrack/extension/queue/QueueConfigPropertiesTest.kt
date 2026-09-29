package net.nemerosa.ontrack.extension.queue

import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import net.nemerosa.ontrack.service.deprecation.DeprecatedConfigurationPropertiesCheck
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import kotlin.test.assertEquals

class QueueConfigPropertiesTest {

    private fun bind(vararg properties: Pair<String, String>): QueueConfigProperties =
        Binder(MapConfigurationPropertySource(properties.toMap()))
            .bindOrCreate(QueueConfigProperties.PREFIX, QueueConfigProperties::class.java)

    private fun deprecations(vararg properties: Pair<String, String>): DeprecationService {
        val deprecationService = mockk<DeprecationService>(relaxed = true)
        val environment = StandardEnvironment().apply {
            propertySources.forEach { propertySources.remove(it.name) }
            propertySources.addLast(MapPropertySource("test", properties.toMap()))
        }
        DeprecatedConfigurationPropertiesCheck(
            environment,
            listOf(QueueDeprecatedConfigurationProperties()),
            deprecationService
        ).check()
        return deprecationService
    }

    @Test
    fun `Warning if sync by default`() {
        assertEquals(true, bind().general.warnIfSync)
    }

    @Test
    fun `Warning if sync disabled`() {
        assertEquals(false, bind("ontrack.extension.queue.general.warn-if-sync" to "false").general.warnIfSync)
    }

    @Test
    fun `Warning if sync disabled using the deprecated name`() {
        assertEquals(false, bind("ontrack.extension.queue.general.warn-if-async" to "false").general.warnIfSync)
    }

    @Test
    fun `The new name wins over the deprecated one`() {
        assertEquals(
            true,
            bind(
                "ontrack.extension.queue.general.warn-if-async" to "false",
                "ontrack.extension.queue.general.warn-if-sync" to "true",
            ).general.warnIfSync
        )
    }

    @Test
    fun `The deprecated name is reported`() {
        val deprecationService = deprecations("ontrack.extension.queue.general.warnIfAsync" to "false")
        verify(exactly = 1) {
            deprecationService.deprecatedUsage(
                DeprecationSurface.CONFIG,
                "ontrack.extension.queue.general.warn-if-async",
                "Removed in V7. Use ontrack.extension.queue.general.warn-if-sync instead. See #1923",
            )
        }
    }

    @Test
    fun `The new name is not reported`() {
        val deprecationService = deprecations("ontrack.extension.queue.general.warn-if-sync" to "false")
        verify(exactly = 0) { deprecationService.deprecatedUsage(any(), any(), any()) }
    }
}
