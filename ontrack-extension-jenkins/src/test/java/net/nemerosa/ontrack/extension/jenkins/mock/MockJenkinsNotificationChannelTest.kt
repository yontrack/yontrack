package net.nemerosa.ontrack.extension.jenkins.mock

import io.mockk.every
import io.mockk.mockk
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfiguration
import net.nemerosa.ontrack.extension.jenkins.JenkinsConfigurationService
import net.nemerosa.ontrack.extension.jenkins.notifications.JenkinsNotificationChannelConfig
import net.nemerosa.ontrack.extension.jenkins.notifications.JenkinsNotificationChannelConfigCallMode
import net.nemerosa.ontrack.extension.jenkins.notifications.JenkinsNotificationChannelConfigParam
import net.nemerosa.ontrack.extension.notifications.channels.NotificationResultType
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventFactoryImpl
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The mock Jenkins channel can be enabled outside of the `dev` profile (see
 * `ontrack.config.extension.jenkins.mock.enabled`), on a demonstration instance where the
 * workflows calling it run their nodes in parallel, and where no Jenkins configuration can be
 * created for it: the connection test of a Jenkins configuration goes to a real Jenkins.
 */
class MockJenkinsNotificationChannelTest {

    private val jenkinsConfigurationService = mockk<JenkinsConfigurationService>()
    private val eventTemplatingService = mockk<EventTemplatingService>()
    private val mockJenkinsClients = MockJenkinsClients()
    private val channel = MockJenkinsNotificationChannel(
        jenkinsConfigurationService = jenkinsConfigurationService,
        eventTemplatingService = eventTemplatingService,
        mockJenkinsClients = mockJenkinsClients,
    )

    @Test
    fun `a Jenkins configuration of that name is used when there is one`() {
        every { jenkinsConfigurationService.findConfiguration(CONFIG) } returns
                JenkinsConfiguration(CONFIG, "https://jenkins.example.com", "user", "password")

        val result = channel.publish("1", config(), promotionRunEvent(), emptyMap(), null) { it }

        assertEquals(NotificationResultType.OK, result.type)
        assertEquals("https://jenkins.example.com/job/canary/job/smoke-tests", result.output?.jobUrl)
    }

    @Test
    fun `without a Jenkins configuration of that name, the mock Jenkins of that name is called`() {
        every { jenkinsConfigurationService.findConfiguration(CONFIG) } returns null

        // Accepted when the workflow is saved...
        val validated = channel.validate(config().asJson())
        assertTrue(validated.isOk(), "Configuration accepted: ${validated.message}")
        // ...and run
        val result = channel.publish("1", config(), promotionRunEvent(), emptyMap(), null) { it }

        assertEquals(NotificationResultType.OK, result.type)
        assertEquals(
            "${MockJenkinsClients.MOCK_URL}/$CONFIG/job/canary/job/smoke-tests",
            result.output?.jobUrl,
        )
        assertNotNull(mockJenkinsClients.findClient(CONFIG)?.jobs?.get(JOB)) { job ->
            assertEquals(1, job.builds.size)
        }
    }

    @Test
    fun `a failing build fails the notification`() {
        every { jenkinsConfigurationService.findConfiguration(CONFIG) } returns null

        val result = channel.publish(
            "1",
            config(parameters = mapOf("result" to "FAILURE")),
            promotionRunEvent(),
            emptyMap(),
            null,
        ) { it }

        assertEquals(NotificationResultType.ERROR, result.type)
    }

    @Test
    fun `concurrent builds of the same job are all recorded`() {
        every { jenkinsConfigurationService.findConfiguration(CONFIG) } returns null
        val event = promotionRunEvent()

        val count = 64
        val pool = Executors.newFixedThreadPool(16)
        val start = CountDownLatch(1)
        val done = CountDownLatch(count)
        try {
            repeat(count) {
                pool.submit {
                    start.await()
                    channel.publish("1", config(), event, emptyMap(), null) { it }
                    done.countDown()
                }
            }
            start.countDown()
            check(done.await(30, TimeUnit.SECONDS)) { "Builds did not complete in time" }
        } finally {
            pool.shutdownNow()
        }

        val builds = mockJenkinsClients.findClient(CONFIG)?.jobs?.get(JOB)?.builds
        assertEquals((1..count).toList(), builds?.map { it.number }?.sorted())
    }

    @Test
    fun `waiting time is clamped`() {
        assertEquals(0L, MockJenkinsClient.clampWaiting(-1L))
        assertEquals(1_500L, MockJenkinsClient.clampWaiting(1_500L))
        assertEquals(MockJenkinsClient.MAX_WAITING_MS, MockJenkinsClient.clampWaiting(Long.MAX_VALUE))
    }

    private fun config(parameters: Map<String, String> = emptyMap()) = JenkinsNotificationChannelConfig(
        config = CONFIG,
        job = JOB,
        parameters = parameters.map { (name, value) -> JenkinsNotificationChannelConfigParam(name, value) },
        callMode = JenkinsNotificationChannelConfigCallMode.SYNC,
    )

    private fun promotionRunEvent(): Event {
        val project = Project.of(NameDescription.nd("project", "")).withId(ID.of(1))
        val branch = Branch.of(project, NameDescription.nd("main", "")).withId(ID.of(10))
        val promotionLevel = PromotionLevel.of(branch, NameDescription.nd("CANARY", "")).withId(ID.of(100))
        val build = Build.of(branch, NameDescription.nd("1", ""), Signature.of("test")).withId(ID.of(1000))
        val promotionRun = PromotionRun.of(build, promotionLevel, Signature.of("test"), null).withId(ID.of(10000))
        every { eventTemplatingService.render(any(), any(), any(), any()) } answers {
            it.invocation.args.first() as String
        }
        return EventFactoryImpl().newPromotionRun(promotionRun)
    }

    companion object {
        private const val CONFIG = "demo"
        private const val JOB = "canary/smoke-tests"
    }
}
