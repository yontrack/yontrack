package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.extension.api.CacheConfigExtension
import org.springframework.stereotype.Component

/**
 * List of core caches.
 *
 * Their entries expire quickly, so that what an instance writes is seen by another instance
 * running side by side within this delay.
 *
 * The settings are not cached: they are read once per unit of work (#2080, ADR 0021).
 */
@Component
class CoreCacheConfigExtension : CacheConfigExtension {
    override val caches: Map<String, String>
        get() = mapOf(
            PROPERTIES to "maximumSize=1000,expireAfterWrite=5m,recordStats",
        )

    companion object {
        /**
         * Cache of the properties of the entities, as named by `PropertyJdbcRepository.CACHE_PROPERTIES`.
         */
        const val PROPERTIES = "properties"
    }
}
