package net.nemerosa.ontrack.demo.seed

import net.nemerosa.ontrack.json.asJson
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The HTTP call channel in the demo (#2156): the GOLD promotions of `petclinic`'s main branch start a
 * deployment on the instance's own mock deployment API, through the `demo-deployments` configuration,
 * and wait for it to be over.
 */
class DemoHttpCallSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private fun seed(target: DemoTarget) = DemoSeed(target, clock, log = {})

    private fun seeded(): InMemoryDemoTarget =
        InMemoryDemoTarget().also { seed(it).run(DemoContent.dataset(emptyList())) }

    private fun InMemoryDemoTarget.branch(name: String): InMemoryDemoTarget.InMemoryBranch =
        projects()
            .map { it as InMemoryDemoTarget.InMemoryProject }
            .single { it.name == DemoContent.SERVICE }
            .branches.single { it.name == name }

    private val deploy: HttpCallSpec
        get() = DemoContent.dataset(emptyList()).projects
            .single { it.name == DemoContent.SERVICE }
            .branches.single { it.name == DemoContent.MAIN }
            .promotionLevels.single { it.name == DemoContent.GOLD }
            .httpCalls.single()

    @Test
    fun `the deployments configuration points at the instance's own mock deployment API`() {
        val target = seeded()
        assertEquals(
            mapOf(DemoContent.DEPLOYMENTS_CONFIG to InMemoryDemoTarget.HTTP_MOCK_URL),
            target.httpConfigurations,
        )
    }

    @Test
    fun `GOLD on the main branch of petclinic starts a deployment and waits for it`() {
        val call = deploy
        assertEquals(DemoContent.DEPLOYMENTS_CONFIG, call.config)
        assertEquals("POST", call.method)
        assertEquals("/deployments", call.path)
        assertEquals("response.headers['location']", call.statusUrl)
        assertEquals("response.body.status == 'SUCCEEDED'", call.successCondition)
        assertEquals("response.body.status == 'FAILED'", call.failureCondition)
        assertTrue(call.intervalSeconds >= HttpCallSpec.MIN_INTERVAL_SECONDS, "Polled no more often than the channel allows")
        // Several polls, and room for the queue
        assertTrue(DemoContent.DEPLOYMENT_SECONDS >= 2 * call.intervalSeconds, "The deployment is polled more than once")
        assertTrue(call.timeoutSeconds >= 5 * DemoContent.DEPLOYMENT_SECONDS, "The timeout is well above the deployment")
    }

    @Test
    fun `the body of the call names the project, the build and the promotion`() {
        val body = assertNotNull(deploy.body)
        assertEquals("\${project}", body.path("project").asString())
        assertEquals("\${build}", body.path("build").asString())
        assertEquals("\${promotionLevel}", body.path("promotion").asString())
        assertEquals(DemoContent.DEPLOYMENT_SECONDS, body.path("durationSeconds").asInt())
    }

    @Test
    fun `every GOLD promotion of the main branch made the call, and it succeeded after several attempts`() {
        val main = seeded().branch(DemoContent.MAIN)
        val gold = main.builds.filter { build -> build.promotions.any { it.first == DemoContent.GOLD } }
        assertTrue(gold.isNotEmpty(), "Builds are promoted to GOLD")
        gold.forEach { build ->
            val call = build.httpCalls.single()
            assertEquals(DemoContent.GOLD, call.promotionLevel)
            assertEquals("${InMemoryDemoTarget.HTTP_MOCK_URL}/deployments", call.url)
            assertTrue(call.succeeded, "The call of ${build.name} succeeded")
            assertTrue(call.attempts > 2, "The call of ${build.name} polled several times: ${call.attempts}")
        }
        // Nothing else calls
        assertTrue(main.builds.filter { it !in gold }.all { it.httpCalls.isEmpty() })
        assertTrue(seeded().branch(DemoContent.MAINTENANCE).builds.all { it.httpCalls.isEmpty() })
    }

    @Test
    fun `the calls are waited for once every promotion has been made, the workflow ones included`() {
        val target = seeded()
        val journal = target.journal
        val awaited = journal.indices.filter { journal[it].startsWith("await http call ") }
        val gold = target.branch(DemoContent.MAIN).builds.count { build -> build.promotions.any { it.first == DemoContent.GOLD } }
        assertEquals(gold, awaited.size, "Every call is waited for: $journal")
        val lastCall = journal.indexOfLast { it.startsWith("http call ") }
        val lastWorkflow = journal.indexOfLast { it.startsWith("workflow ") }
        assertTrue(awaited.first() > lastCall, "The calls are waited for once they are all made: $journal")
        assertTrue(awaited.first() > lastWorkflow, "... after the promotions held for the workflows: $journal")
    }

    @Test
    fun `the deployments configuration is saved before any subscription names it`() {
        val journal = seeded().journal
        assertTrue(journal.indexOf("http configuration ${DemoContent.DEPLOYMENTS_CONFIG}") < journal.indexOfFirst { it.startsWith("http call ") })
    }

    @Test
    fun `an instance without the mock deployment API is refused before anything is deleted`() {
        val target = seeded()
        val before = target.snapshot()

        val error = assertFailsWith<IllegalStateException> {
            seed(WithoutHttpMock(target)).run(DemoContent.dataset(emptyList()))
        }
        assertTrue("mock deployment API" in error.message.orEmpty(), error.message)
        assertTrue("Nothing was deleted" in error.message.orEmpty(), error.message)
        assertEquals(before, target.snapshot(), "The demo is untouched")
    }

    @Test
    fun `a call which does not succeed stops the seed`() {
        val error = assertFailsWith<IllegalStateException> {
            seed(ElsewhereHttpMock(InMemoryDemoTarget())).run(DemoContent.dataset(emptyList()))
        }
        assertTrue("deploy call" in error.message.orEmpty(), error.message)
        assertTrue("failed" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `an HTTP call naming a configuration the dataset does not declare is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            dataset(deploy.copy(config = "unknown")).validate()
        }
        assertTrue("HTTP configuration unknown" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `an HTTP call polling more often than the channel allows is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            dataset(deploy.copy(intervalSeconds = 2)).validate()
        }
        assertTrue("polls every 2 s" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `an HTTP call with a status URL and nothing to wait for is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            dataset(deploy.copy(successCondition = null)).validate()
        }
        assertTrue("no success condition" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `a GET with a body is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            dataset(deploy.copy(method = "GET", statusUrl = null, body = mapOf("a" to "b").asJson())).validate()
        }
        assertTrue("body with a GET" in error.message.orEmpty(), error.message)
    }

    /** A one-project dataset whose only promotion level makes [call]. */
    private fun dataset(call: HttpCallSpec) = DemoDataset(
        projects = listOf(
            ProjectSpec(
                name = "p",
                description = "",
                branches = listOf(
                    BranchSpec(
                        name = "main",
                        description = "",
                        promotionLevels = listOf(PromotionLevelSpec("GOLD", "", httpCalls = listOf(call))),
                    ),
                ),
            ),
        ),
        httpConfigurations = listOf(HttpConfigurationSpec(DemoContent.DEPLOYMENTS_CONFIG)),
    )

    /** An instance which never enabled the mock deployment API. */
    private class WithoutHttpMock(delegate: InMemoryDemoTarget) : DemoTarget by delegate {
        override fun checkHttpMockAvailable(): String =
            InMemoryDemoTarget(httpMockEnabled = false).checkHttpMockAvailable()
    }

    /** An instance telling a URL its backend does not reach its mock under. */
    private class ElsewhereHttpMock(private val delegate: InMemoryDemoTarget) : DemoTarget by delegate {
        override fun checkHttpMockAvailable(): String = "http://elsewhere/extension/notifications/http/mock"
    }
}
