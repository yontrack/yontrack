package net.nemerosa.ontrack.service.support

import net.nemerosa.ontrack.it.AbstractServiceTestSupport
import net.nemerosa.ontrack.job.JobScope
import net.nemerosa.ontrack.model.support.*
import net.nemerosa.ontrack.test.TestUtils.uid
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The connector statuses are collected by a cluster job, on one replica, and stored in the
 * database, from which every replica serves them (#2078).
 */
class ConnectorStatusJobIT : AbstractServiceTestSupport() {

    @Autowired
    private lateinit var storageService: StorageService

    private class TestIndicator(
        override val type: String,
        override val statuses: List<ConnectorStatus>,
    ) : ConnectorStatusIndicator

    private fun indicator(): TestIndicator {
        val type = uid("t-")
        return TestIndicator(
            type = type,
            statuses = listOf(
                ConnectorStatus.ok(ConnectorDescription(Connector(type, "up"), "https://up")),
                ConnectorStatus.error(ConnectorDescription(Connector(type, "down"), "https://down"), RuntimeException("Down")),
            )
        )
    }

    @Test
    fun `One cluster job per indicator`() {
        val indicator = indicator()
        val job = ConnectorStatusJob(listOf(indicator), storageService)
        val registration = job.jobRegistrations.single()
        assertEquals(JobScope.CLUSTER, registration.scope)
        assertEquals(indicator.type, registration.job.key.id)
    }

    @Test
    fun `Statuses collected by one replica are served by another`() {
        val indicator = indicator()
        // Two replicas, sharing the database
        val one = ConnectorStatusJob(listOf(indicator), storageService)
        val other = ConnectorStatusJob(listOf(indicator), storageService)

        assertTrue(other.globalStatus.statuses.isEmpty(), "Nothing collected yet")

        one.jobRegistrations.single().job.task.run { }

        val status = other.globalStatus
        assertEquals(2, status.count)
        assertEquals(1, status.upCount)
        assertEquals(1, status.downCount)
        assertEquals(
            listOf("down" to "Down", "up" to null),
            status.statuses.map { it.status.description.connector.name to it.status.error }
        )
        assertEquals(
            listOf("https://down", "https://up"),
            status.statuses.map { it.status.description.connection }
        )
        assertEquals(2, other.statuses(indicator.type)?.size)
    }

    @Test
    fun `Collecting all the statuses at once`() {
        val first = indicator()
        val second = indicator()
        val one = ConnectorStatusJob(listOf(first, second), storageService)
        one.collect()
        val other = ConnectorStatusJob(listOf(first, second), storageService)
        assertEquals(4, other.globalStatus.count)
    }
}
