package net.nemerosa.ontrack.extension.git.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.git.GitConfigProperties
import net.nemerosa.ontrack.extension.git.model.GitPullRequest
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.EntityStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DefaultGitPullRequestCacheTest {

    private lateinit var entityStore: EntityStore
    private lateinit var gitConfigProperties: GitConfigProperties
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var cache: DefaultGitPullRequestCache

    private val branch = mockk<Branch>()

    private val storedPr = pr(1, "Stored PR")
    private val scmPr = pr(1, "SCM PR")

    private var providerCalls = 0
    private val provider: () -> GitPullRequest? = {
        providerCalls++
        scmPr
    }

    @BeforeEach
    fun init() {
        entityStore = mockk(relaxed = true)
        gitConfigProperties = GitConfigProperties()
        meterRegistry = SimpleMeterRegistry()
        cache = DefaultGitPullRequestCache(entityStore, gitConfigProperties)
        cache.bindTo(meterRegistry)
    }

    @Test
    fun `Cache enabled by default with a duration of 6 hours`() {
        assertTrue(gitConfigProperties.pullRequests.cache.enabled)
        assertEquals(Duration.ofHours(6), gitConfigProperties.pullRequests.cache.duration)
    }

    @Test
    fun `Cache enabled with a fresh entry returns the stored PR without calling the SCM`() {
        storedEntry(expirationTime = Time.now().plusHours(1))

        val pr = cache.getBranchPullRequest(branch, provider)

        assertSame(storedPr, pr)
        assertEquals(0, providerCalls, "SCM not called")
        verify(exactly = 0) { entityStore.store(any(), any(), any(), any()) }
        assertEquals(1.0, counter(GitPullRequestCacheMetrics.git_pr_cache_hits))
        assertEquals(0.0, counter(GitPullRequestCacheMetrics.git_pr_cache_miss))
    }

    @Test
    fun `Cache enabled with an expired entry calls the SCM and stores the PR`() {
        storedEntry(expirationTime = Time.now().minusHours(1))

        val pr = cache.getBranchPullRequest(branch, provider)

        assertSame(scmPr, pr)
        assertEquals(1, providerCalls, "SCM called")
        assertStored()
        assertEquals(0.0, counter(GitPullRequestCacheMetrics.git_pr_cache_hits))
        assertEquals(1.0, counter(GitPullRequestCacheMetrics.git_pr_cache_miss))
    }

    @Test
    fun `Cache enabled with no entry calls the SCM and stores the PR`() {
        every { entityStore.findByName<Any>(branch, any(), any(), any()) } returns null

        val pr = cache.getBranchPullRequest(branch, provider)

        assertSame(scmPr, pr)
        assertEquals(1, providerCalls, "SCM called")
        assertStored()
        assertEquals(0.0, counter(GitPullRequestCacheMetrics.git_pr_cache_hits))
        assertEquals(1.0, counter(GitPullRequestCacheMetrics.git_pr_cache_miss))
    }

    @Test
    fun `Cache enabled with no PR in the SCM returns no PR and stores nothing`() {
        every { entityStore.findByName<Any>(branch, any(), any(), any()) } returns null

        val pr = cache.getBranchPullRequest(branch) {
            providerCalls++
            null
        }

        assertNull(pr)
        assertEquals(1, providerCalls, "SCM called")
        verify(exactly = 0) { entityStore.store(any(), any(), any(), any()) }
        assertEquals(1.0, counter(GitPullRequestCacheMetrics.git_pr_cache_miss))
    }

    @Test
    fun `Cache disabled calls the SCM and leaves the store untouched`() {
        gitConfigProperties.pullRequests.cache.enabled = false
        storedEntry(expirationTime = Time.now().plusHours(1))

        val pr = cache.getBranchPullRequest(branch, provider)

        assertSame(scmPr, pr)
        assertEquals(1, providerCalls, "SCM called")
        verify(exactly = 0) { entityStore.findByName<Any>(any(), any(), any(), any()) }
        verify(exactly = 0) { entityStore.store(any(), any(), any(), any()) }
        assertEquals(0.0, counter(GitPullRequestCacheMetrics.git_pr_cache_hits))
        assertEquals(0.0, counter(GitPullRequestCacheMetrics.git_pr_cache_miss))
    }

    private fun storedEntry(expirationTime: java.time.LocalDateTime) {
        every { entityStore.findByName<Any>(branch, any(), any(), any()) } returns
                DefaultGitPullRequestCache.StoredGitPullRequest(pr = storedPr, expirationTime = expirationTime)
    }

    private fun assertStored() {
        val data = slot<Any>()
        verify(exactly = 1) { entityStore.store(branch, any(), EntityStore.DEFAULT_NAME, capture(data)) }
        val stored = data.captured as DefaultGitPullRequestCache.StoredGitPullRequest
        assertSame(scmPr, stored.pr)
        assertTrue(stored.expirationTime > Time.now().plusHours(5), "Expiration set from the configured duration")
    }

    private fun counter(name: String): Double =
        meterRegistry.find(name).counter()?.count() ?: 0.0

    private fun pr(id: Int, title: String) = GitPullRequest(
        id = id,
        key = "#$id",
        source = "feature/$id",
        target = "main",
        title = title,
        status = "open",
        url = "uri:pr:$id",
    )
}
