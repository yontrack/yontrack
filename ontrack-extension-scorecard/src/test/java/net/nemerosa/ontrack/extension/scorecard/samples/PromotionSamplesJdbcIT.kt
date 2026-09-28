package net.nemerosa.ontrack.extension.scorecard.samples

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.chart.support.Interval
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class PromotionSamplesJdbcIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var promotionSamples: PromotionSamples

    private val now = Time.now.withNano(0)
    private val window = Interval(now.minusDays(30), now)

    @Test
    fun `Lead time from build creation to its first promotion in the window`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val other = promotionLevel()
                    // Promoted twice: only the first promotion counts
                    val b1 = build()
                    b1.updateBuildSignature(time = now.minusDays(10))
                    b1.promote(pl, time = now.minusDays(9))
                    b1.promote(pl, time = now.minusDays(2))
                    // Promoted to another level only
                    val b2 = build()
                    b2.updateBuildSignature(time = now.minusDays(8))
                    b2.promote(other, time = now.minusDays(7))
                    // Created before the window, promoted in it
                    val b3 = build()
                    b3.updateBuildSignature(time = now.minusDays(40))
                    b3.promote(pl, time = now.minusDays(20))
                    // First promoted before the window, promoted again in it: out
                    val b4 = build()
                    b4.updateBuildSignature(time = now.minusDays(50))
                    b4.promote(pl, time = now.minusDays(45))
                    b4.promote(pl, time = now.minusDays(5))
                    // Not promoted
                    build().updateBuildSignature(time = now.minusDays(3))

                    val samples = promotionSamples.leadTimes(listOf(pl), window)
                    assertEquals(
                        listOf(
                            DurationSample(id(), now.minusDays(40), now.minusDays(20)),
                            DurationSample(id(), now.minusDays(10), now.minusDays(9)),
                        ),
                        samples
                    )
                    assertEquals(listOf(20 * 86400.0, 86400.0), samples.map { it.seconds })
                }
            }
        }
    }

    @Test
    fun `Lead times pooled across the levels of several branches`() {
        asAdmin {
            project {
                val levels = (1..2).map { n ->
                    val branch = branch("b$n")
                    val pl = branch.promotionLevel()
                    val build = branch.build()
                    build.updateBuildSignature(time = now.minusDays(10L + n))
                    build.promote(pl, time = now.minusDays(10))
                    pl
                }
                val samples = promotionSamples.leadTimes(levels, window)
                assertEquals(levels.map { it.branch.id() }.toSet(), samples.map { it.branchId }.toSet())
                assertEquals(setOf(86400.0, 2 * 86400.0), samples.map { it.seconds }.toSet())
            }
        }
    }

    @Test
    fun `Every promotion run at the level in the window`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val b1 = build()
                    b1.promote(pl, time = now.minusDays(40))
                    b1.promote(pl, time = now.minusDays(20))
                    val b2 = build()
                    b2.promote(pl, time = now.minusDays(10))
                    b2.promote(promotionLevel(), time = now.minusDays(10))

                    val samples = promotionSamples.promotions(listOf(pl), window)
                    assertEquals(
                        listOf(
                            EventSample(id(), now.minusDays(20)),
                            EventSample(id(), now.minusDays(10)),
                        ),
                        samples
                    )
                }
            }
        }
    }

    @Test
    fun `Builds created in the window, with their first promotion at the level before its end`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    val other = promotionLevel()
                    // Created before the window: out
                    build().apply {
                        updateBuildSignature(time = now.minusDays(40))
                        promote(pl, time = now.minusDays(39))
                    }
                    // Promoted twice: the first promotion
                    val b1 = build().apply {
                        updateBuildSignature(time = now.minusDays(20))
                        promote(pl, time = now.minusDays(19))
                        promote(pl, time = now.minusDays(2))
                    }
                    // Promoted to another level only
                    val b2 = build().apply {
                        updateBuildSignature(time = now.minusDays(10))
                        promote(other, time = now.minusDays(9))
                    }
                    // Not promoted
                    val b3 = build().apply {
                        updateBuildSignature(time = now.minusDays(5))
                    }

                    assertEquals(
                        listOf(
                            BuildSample(id(), b1.id(), now.minusDays(20), now.minusDays(19)),
                            BuildSample(id(), b2.id(), now.minusDays(10), null),
                            BuildSample(id(), b3.id(), now.minusDays(5), null),
                        ),
                        promotionSamples.builds(listOf(pl), window)
                    )
                    // Promotion after the end of the interval: not known yet
                    assertEquals(
                        listOf(BuildSample(id(), b1.id(), now.minusDays(20), null)),
                        promotionSamples.builds(listOf(pl), Interval(now.minusDays(30), now.minusDays(19)))
                    )
                }
            }
        }
    }

    @Test
    fun `Outages restored in the window, started before it, and still going on`() {
        asAdmin {
            project {
                val main = branch("main")
                val release = branch("release")
                val gold = main.promotionLevel("GOLD")
                val platinum = release.promotionLevel("PLATINUM")
                main.apply {
                    // B1 promoted, B2..B4 unpromoted, B5 promoted - outage started before the window
                    build().apply {
                        updateBuildSignature(time = now.minusDays(50))
                        promote(gold, time = now.minusDays(49))
                    }
                    build().updateBuildSignature(time = now.minusDays(40))
                    build().updateBuildSignature(time = now.minusDays(35))
                    build().updateBuildSignature(time = now.minusDays(25))
                    build().apply {
                        updateBuildSignature(time = now.minusDays(20))
                        promote(gold, time = now.minusDays(18))
                    }
                    // Still going on
                    build().updateBuildSignature(time = now.minusDays(5))
                }
                release.apply {
                    build().apply {
                        updateBuildSignature(time = now.minusDays(15))
                        promote(platinum, time = now.minusDays(15))
                    }
                    build().updateBuildSignature(time = now.minusDays(12))
                    build().apply {
                        updateBuildSignature(time = now.minusDays(10))
                        promote(platinum, time = now.minusDays(9))
                    }
                }

                val outages = promotionSamples.outages(listOf(gold, platinum), window)
                assertEquals(
                    listOf(
                        OutageSample(main.id(), now.minusDays(40), now.minusDays(18)),
                        OutageSample(release.id(), now.minusDays(12), now.minusDays(9)),
                        OutageSample(main.id(), now.minusDays(5), null),
                    ),
                    outages
                )
                assertEquals(listOf(22 * 86400.0, 3 * 86400.0), outages.mapNotNull { it.timeToRestore?.seconds })
            }
        }
    }

    @Test
    fun `No level, no sample`() {
        assertEquals(emptyList(), promotionSamples.leadTimes(emptyList(), window))
        assertEquals(emptyList(), promotionSamples.promotions(emptyList(), window))
        assertEquals(emptyList(), promotionSamples.builds(emptyList(), window))
        assertEquals(emptyList(), promotionSamples.outages(emptyList(), window))
    }
}
