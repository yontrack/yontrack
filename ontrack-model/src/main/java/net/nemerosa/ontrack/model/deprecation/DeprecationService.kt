package net.nemerosa.ontrack.model.deprecation

/**
 * Records the usage of a deprecated external item, so that an administrator can find out,
 * before upgrading, what the next major version removes and is still used.
 *
 * Only external contracts are reported: what a user writes or calls (GraphQL, REST, configuration,
 * CasC, environment, templating, settings, properties, CI configuration). Deprecated internal code
 * is never reported.
 */
interface DeprecationService {

    /**
     * Records one usage of a deprecated item: increments the [DeprecationMetrics.usage] counter
     * and logs a warning, once per item for the lifetime of the JVM.
     *
     * @param surface Surface through which the item was reached
     * @param item Identifier of the item, stable across calls (e.g. `PromotionLevel.promotionRuns`)
     * @param message Deprecation marker: `Removed in V<N>. Use X instead. See #NNNN`
     */
    fun deprecatedUsage(surface: DeprecationSurface, item: String, message: String)

}
