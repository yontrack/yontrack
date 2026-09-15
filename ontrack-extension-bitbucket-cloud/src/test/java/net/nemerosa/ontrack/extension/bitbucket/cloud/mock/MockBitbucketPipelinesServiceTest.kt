package net.nemerosa.ontrack.extension.bitbucket.cloud.mock

import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudAuthType
import net.nemerosa.ontrack.extension.bitbucket.cloud.configuration.BitbucketCloudConfiguration
import net.nemerosa.ontrack.extension.bitbucket.cloud.pipelines.FakeTicker
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

class MockBitbucketPipelinesServiceTest {

    private val configuration = BitbucketCloudConfiguration(
        name = "bbc",
        authType = BitbucketCloudAuthType.ACCESS_TOKEN,
        token = "token",
    )

    private val recorder = MockBitbucketPipelinesRecorder()
    private val ticker = FakeTicker()
    private val service = MockBitbucketPipelinesService(recorder, ticker)

    private fun run(variables: Map<String, String>, timeoutSeconds: Long = 60): Pair<String, Boolean> {
        val trigger = service.trigger(configuration, "ws", "repo", "main", "yontrack-echo", variables)
        val completion = service.waitForCompletion(
            configuration, "ws", "repo", trigger.uuid, Duration.ofSeconds(timeoutSeconds)
        )
        return completion.status.state to completion.timedOut
    }

    @Test
    fun `Triggered pipelines are recorded`() {
        service.trigger(configuration, "ws", "repo", "main", "yontrack-echo", mapOf("MESSAGE" to "hello"))
        service.trigger(configuration, "ws", "repo", "develop", null, emptyMap())

        val runs = recorder.findRuns("bbc", "ws", "repo")
        assertEquals(listOf("main", "develop"), runs.map { it.branch })
        assertEquals(listOf("yontrack-echo", null), runs.map { it.pipeline })
        assertEquals(mapOf("MESSAGE" to "hello"), runs.first().variables)
        assertEquals(emptyList(), recorder.findRuns("bbc", "ws", "other"))
    }

    @Test
    fun `Successful by default and immediate`() {
        assertEquals("SUCCESSFUL" to false, run(emptyMap()))
        assertEquals(listOf(0L), ticker.sleeps)
    }

    @Test
    fun `Result and duration driven by the variables`() {
        assertEquals(
            "FAILED" to false,
            run(mapOf("MOCK_RESULT" to "FAILED", "MOCK_DURATION_SECONDS" to "5"))
        )
        assertEquals(listOf(5_000L), ticker.sleeps)
    }

    @Test
    fun `Longer than the timeout`() {
        assertEquals(
            "IN_PROGRESS" to true,
            run(mapOf("MOCK_DURATION_SECONDS" to "120"), timeoutSeconds = 30)
        )
        assertEquals(listOf(30_000L), ticker.sleeps)
    }
}
