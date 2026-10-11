package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.common.Caches
import net.nemerosa.ontrack.extension.api.CacheConfigExtension
import org.springframework.stereotype.Component

/**
 * List of core caches.
 *
 * Their entries expire quickly, so that what an instance writes is seen by another instance
 * running side by side, as during a rolling upgrade, within this delay.
 */
@Component
class CoreCacheConfigExtension : CacheConfigExtension {
    override val caches: Map<String, String>
        get() = mapOf(
            // One entry per settings type: the size is above the number of settings types (one
            // per `SettingsProvider`), so that reading some settings never evicts others.
            Caches.SETTINGS to "maximumSize=1000,expireAfterWrite=1m,recordStats",
            PROPERTIES to "maximumSize=1000,expireAfterWrite=5m,recordStats",
        )

    companion object {
        /**
         * Cache of the properties of the entities, as named by `PropertyJdbcRepository.CACHE_PROPERTIES`.
         */
        const val PROPERTIES = "properties"
    }
}
