package net.nemerosa.ontrack.service.security

/**
 * Versions of the keys of the confidential store, shared by all the replicas, whatever the store
 * (#2086).
 *
 * The version of a key is bumped each time the key is generated or imported, so that a replica
 * holding an older version loads the key again from the store.
 */
interface ConfidentialKeyVersions {

    /**
     * Current version of the key, `null` when none was ever recorded.
     */
    fun getVersion(id: String): Long?

    /**
     * Runs the [code] while no other replica runs it for the same key, in a transaction of its own,
     * committed before returning: what the [code] writes - to the store, and with [bump] - is seen
     * by the next one to run it.
     */
    fun <T> exclusively(id: String, code: () -> T): T

    /**
     * Bumps the version of the key, from within [exclusively], and returns the new one.
     */
    fun bump(id: String): Long
}
