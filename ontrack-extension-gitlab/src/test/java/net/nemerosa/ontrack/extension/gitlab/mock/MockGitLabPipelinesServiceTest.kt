package net.nemerosa.ontrack.extension.gitlab.mock

import net.nemerosa.ontrack.extension.gitlab.model.GitLabConfiguration
import net.nemerosa.ontrack.extension.gitlab.pipelines.FakeGitLabTicker
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

class MockGitLabPipelinesServiceTest {

    private val configuration = GitLabConfiguration(
        name = "gl",
        url = "https://gitlab.com",
        token = "token",
    )

    private val recorder = MockGitLabPipelinesRecorder()
    private val ticker = FakeGitLabTicker()
    private val service = MockGitLabPipelinesService(recorder, ticker)

    private fun run(variables: Map<String, String>, timeoutSeconds: Long = 60): Pair<String, Boolean> {
        val trigger = service.trigger(configuration, "group/project", "main", variables)
        val completion = service.waitForCompletion(
            configuration, "group/project", trigger.id, Duration.ofSeconds(timeoutSeconds)
        )
        return completion.status.status to completion.timedOut
    }

    @Test
    fun `Triggered pipelines are recorded`() {
        service.trigger(configuration, "group/project", "main", mapOf("VERSION" to "1.0.0"))
        service.trigger(configuration, "group/project", "develop", emptyMap())

        val runs = recorder.findRuns("gl", "group/project")
        assertEquals(listOf("main", "develop"), runs.map { it.ref })
        assertEquals(mapOf("VERSION" to "1.0.0"), runs.first().variables)
        assertEquals(emptyList(), recorder.findRuns("gl", "group/other"))
    }

    @Test
    fun `The iid counts inside the project while the id counts in the instance`() {
        service.trigger(configuration, "group/one", "main", emptyMap())
        service.trigger(configuration, "group/two", "main", emptyMap())
        service.trigger(configuration, "group/one", "main", emptyMap())

        assertEquals(listOf(1L, 2L), recorder.findRuns("gl", "group/one").map { it.iid })
        assertEquals(listOf(1L, 3L), recorder.findRuns("gl", "group/one").map { it.id })
        assertEquals(listOf(1L), recorder.findRuns("gl", "group/two").map { it.iid })
    }

    @Test
    fun `The pipeline URL is built off the configuration`() {
        val trigger = service.trigger(configuration, "group/sub/project", "main", emptyMap())
        assertEquals("https://gitlab.com/group/sub/project/-/pipelines/1", trigger.url)
    }

    @Test
    fun `Successful by default and immediate`() {
        assertEquals("success" to false, run(emptyMap()))
        assertEquals(listOf(0L), ticker.sleeps)
    }

    @Test
    fun `Status and duration driven by the variables`() {
        assertEquals(
            "failed" to false,
            run(mapOf("MOCK_STATUS" to "failed", "MOCK_DURATION_SECONDS" to "5"))
        )
        assertEquals(listOf(5_000L), ticker.sleeps)
    }

    @Test
    fun `Longer than the timeout`() {
        assertEquals(
            "running" to true,
            run(mapOf("MOCK_DURATION_SECONDS" to "120"), timeoutSeconds = 30)
        )
        assertEquals(listOf(30_000L), ticker.sleeps)
    }
}
