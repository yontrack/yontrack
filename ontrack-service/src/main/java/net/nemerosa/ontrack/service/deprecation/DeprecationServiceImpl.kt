package net.nemerosa.ontrack.service.deprecation

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.model.deprecation.DeprecationMetrics
import net.nemerosa.ontrack.model.deprecation.DeprecationService
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class DeprecationServiceImpl(
    private val meterRegistry: MeterRegistry,
) : DeprecationService {

    private val logger: Logger = LoggerFactory.getLogger(DeprecationService::class.java)

    /**
     * Items already logged during the lifetime of this JVM.
     */
    private val logged = ConcurrentHashMap.newKeySet<Pair<DeprecationSurface, String>>()

    override fun deprecatedUsage(surface: DeprecationSurface, item: String, message: String) {
        meterRegistry.counter(
            DeprecationMetrics.usage,
            "surface", surface.tag,
            "item", item,
        ).increment()
        if (logged.add(surface to item)) {
            logger.warn("[deprecation] Deprecated $item used through ${surface.tag}: $message")
        }
    }
}
