package net.nemerosa.ontrack.it

import io.micrometer.core.instrument.MeterRegistry
import net.nemerosa.ontrack.model.deprecation.DeprecationMetrics
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface

/**
 * Number of times a deprecated item has been reported through a surface since the start of the
 * test context. Compare a value before and after the code under test.
 */
fun MeterRegistry.deprecatedUsageCount(surface: DeprecationSurface, item: String): Double =
    find(DeprecationMetrics.usage)
        .tag("surface", surface.tag)
        .tag("item", item)
        .counter()?.count() ?: 0.0

/**
 * Number of times the [code] reports a deprecated item through a surface.
 */
fun MeterRegistry.deprecatedUsages(surface: DeprecationSurface, item: String, code: () -> Unit): Double {
    val before = deprecatedUsageCount(surface, item)
    code()
    return deprecatedUsageCount(surface, item) - before
}
