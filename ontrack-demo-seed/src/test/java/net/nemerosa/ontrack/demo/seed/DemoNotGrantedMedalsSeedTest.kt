package net.nemerosa.ontrack.demo.seed

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The not-granted medals are off by default, so the seed asks for them: on the sample service alone,
 * and with the global setting left off, so that the demo shows both the feature and the default.
 */
class DemoNotGrantedMedalsSeedTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-01T10:15:30Z"), ZoneOffset.UTC)

    private fun seeded(): InMemoryDemoTarget =
        InMemoryDemoTarget().also { DemoSeed(it, clock, log = {}).run(DemoContent.dataset(emptyList())) }

    @Test
    fun `the medals are enabled on the sample service alone`() {
        assertEquals(
            listOf(DemoContent.SERVICE),
            seeded().projects()
                .filterIsInstance<InMemoryDemoTarget.InMemoryProject>()
                .filter { it.notGrantedMedals }
                .map { it.name },
        )
    }

    @Test
    fun `the builds not satisfying SILVER get its medal, which needs its auto promotion`() {
        val main = DemoContent.dataset(emptyList()).projects
            .single { it.name == DemoContent.SERVICE }
            .branches.single { it.name == DemoContent.MAIN }

        assertTrue(main.promotionLevels.single { it.name == DemoContent.SILVER }.autoPromotion != null)
        listOf("103", "106").forEach { name ->
            assertTrue(
                DemoContent.SILVER !in main.builds.single { it.name == name }.promotionLevels,
                "Build $name is not SILVER",
            )
        }
    }
}
