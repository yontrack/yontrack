package net.nemerosa.ontrack.service.deprecation

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.nemerosa.ontrack.model.deprecation.DeprecationMetrics
import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals

class DeprecationServiceImplTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var service: DeprecationServiceImpl
    private lateinit var appender: ListAppender<ILoggingEvent>
    private val logger = LoggerFactory.getLogger(DeprecationService::class.java) as Logger

    @BeforeEach
    fun before() {
        meterRegistry = SimpleMeterRegistry()
        service = DeprecationServiceImpl(meterRegistry)
        appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
    }

    @AfterEach
    fun after() {
        logger.detachAppender(appender)
    }

    private fun count(surface: String, item: String): Double =
        meterRegistry.find(DeprecationMetrics.usage)
            .tag("surface", surface)
            .tag("item", item)
            .counter()?.count() ?: 0.0

    @Test
    fun `Every usage is counted, per surface and item`() {
        service.deprecatedUsage(DeprecationSurface.GRAPHQL, "Account.name", "Removed in V6.")
        service.deprecatedUsage(DeprecationSurface.GRAPHQL, "Account.name", "Removed in V6.")
        service.deprecatedUsage(DeprecationSurface.TEMPLATING, "user.name", "Removed in V6.")

        assertEquals(2.0, count("graphql", "Account.name"))
        assertEquals(1.0, count("templating", "user.name"))
        assertEquals(0.0, count("templating", "Account.name"))
    }

    @Test
    fun `A warning is logged once per item`() {
        service.deprecatedUsage(DeprecationSurface.GRAPHQL, "Account.name", "Removed in V6. Use email instead. See #1921")
        service.deprecatedUsage(DeprecationSurface.GRAPHQL, "Account.name", "Removed in V6. Use email instead. See #1921")
        service.deprecatedUsage(DeprecationSurface.REST, "POST /rest/x/image", "Removed in V6. Use PUT instead. See #1922")

        val warnings = appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
        assertEquals(
            listOf(
                "[deprecation] Deprecated Account.name used through graphql: Removed in V6. Use email instead. See #1921",
                "[deprecation] Deprecated POST /rest/x/image used through rest: Removed in V6. Use PUT instead. See #1922",
            ),
            warnings
        )
    }
}
