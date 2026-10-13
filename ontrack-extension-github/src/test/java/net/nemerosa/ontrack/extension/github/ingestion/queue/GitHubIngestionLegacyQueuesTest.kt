@file:Suppress("DEPRECATION")

package net.nemerosa.ontrack.extension.github.ingestion.queue

import net.nemerosa.ontrack.extension.github.ingestion.IngestionConfigProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GitHubIngestionLegacyQueuesTest {

    private val probe = listOf("github.ingestion.default") + (0 until 32).map { "github.ingestion.default.$it" }

    @Test
    fun `Old exchange`() {
        assertEquals(
            setOf("github.ingestion"),
            GitHubIngestionLegacyQueues(IngestionConfigProperties()).legacyExchanges,
        )
    }

    @Test
    fun `Old queues probed without configuration`() {
        assertEquals(
            probe.toSet(),
            GitHubIngestionLegacyQueues(IngestionConfigProperties()).legacyQueues,
        )
    }

    @Test
    fun `Old queues from the configuration`() {
        val properties = IngestionConfigProperties().apply {
            processing.scale = 40
            processing.repositories = mapOf(
                "specific" to IngestionConfigProperties.RepositoryQueueConfig(owner = "owner"),
            )
        }
        assertEquals(
            (probe + (32 until 40).map { "github.ingestion.default.$it" } + "github.ingestion.specific").toSet(),
            GitHubIngestionLegacyQueues(properties).legacyQueues,
        )
    }

}
