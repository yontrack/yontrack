package net.nemerosa.ontrack.service.search

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.nemerosa.ontrack.job.JobScheduler
import net.nemerosa.ontrack.model.structure.SearchDocumentIndexer
import net.nemerosa.ontrack.model.support.OntrackConfigProperties
import net.nemerosa.ontrack.model.support.StorageService
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class SearchDocumentStartupRebuildTest {

    private fun indexer(type: String): SearchDocumentIndexer = mockk(relaxed = true) {
        every { searchResultType.id } returns type
        every { documentVersion } returns 1
    }

    @Test
    fun `A type whose rebuild cannot be fired does not prevent the others, nor the startup`() {
        val failing = indexer("failing")
        val other = indexer("other")
        val failingKey = SearchDocumentStartupRebuild.startupRebuildKey(failing)
        val otherKey = SearchDocumentStartupRebuild.startupRebuildKey(other)
        val storageService = mockk<StorageService> {
            every { find(any(), any(), SearchDocumentStartupRebuild.SearchDocumentRebuildStatus::class) } returns null
        }
        val jobScheduler = mockk<JobScheduler> {
            every { fireLater(failingKey, any()) } throws
                    IllegalStateException("Cannot fire")
            every { fireLater(otherKey, any()) } returns
                    Optional.of(CompletableFuture<Any?>())
        }
        val rebuild = SearchDocumentStartupRebuild(
            searchDocumentIndexers = listOf(failing, other),
            searchDocumentService = mockk(),
            storageService = storageService,
            ontrackConfigProperties = OntrackConfigProperties(),
            jobScheduler = jobScheduler,
        )
        assertEquals(1, rebuild.launch(Duration.ofMinutes(1)), "The other type is fired")
        verify { jobScheduler.fireLater(otherKey, any()) }
    }
}
