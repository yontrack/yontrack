package net.nemerosa.ontrack.service.security

import java.util.concurrent.ConcurrentHashMap

/**
 * Versions of the keys for the unit tests: one replica, no database.
 */
class InMemoryConfidentialKeyVersions : ConfidentialKeyVersions {

    private val versions = ConcurrentHashMap<String, Long>()

    override fun getVersion(id: String): Long? = versions[id]

    @Synchronized
    override fun <T> exclusively(id: String, code: () -> T): T = code()

    override fun bump(id: String): Long = versions.merge(id, 1L, Long::plus)!!
}
