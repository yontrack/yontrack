package net.nemerosa.ontrack.model.settings

/**
 * Reading the settings, by type.
 *
 * Despite its name, this service keeps no cache across requests: several replicas run at once, and
 * a cache would keep, for some time, settings changed through another one - a revoked permission,
 * a rotated secret (ADR 0021). The settings are read once per unit of work - an HTTP request, the
 * processing of a queue message - through the `ReadMemo`, and fresh on every read outside of one,
 * as in a job.
 */
interface CachedSettingsService {

    /**
     * Settings of the given [type], read once per unit of work.
     */
    fun <T> getCachedSettings(type: Class<T>): T

    /**
     * The settings of the given [type] are being written: the current unit of work reads them
     * again.
     */
    fun <T> invalidate(type: Class<T>)

}
