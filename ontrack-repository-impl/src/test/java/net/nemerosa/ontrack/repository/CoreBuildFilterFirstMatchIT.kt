package net.nemerosa.ontrack.repository

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.PromotionLevel
import net.nemerosa.ontrack.model.structure.PropertyType
import net.nemerosa.ontrack.model.structure.StandardBuildFilterData
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.jvm.optionals.getOrNull
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The first build, in build order, matching a filter between two bounds.
 */
@AsAdminTest
class CoreBuildFilterFirstMatchIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var coreBuildFilterRepository: CoreBuildFilterRepository

    private val noProperty: (String) -> PropertyType<*> = { error("No property expected: $it") }

    private fun Branch.first(
        data: StandardBuildFilterData,
        from: BuildBound,
        until: BuildBound? = null,
    ): Build? = coreBuildFilterRepository.firstMatchingBuild(this, data, from, until, null, noProperty)

    private fun Build.bound() = BuildBound.ById(id())

    private fun Branch.goldLevel(): PromotionLevel =
        structureService.findPromotionLevelByName(project.name, name, "GOLD").getOrNull()
            ?: promotionLevel("GOLD")

    private fun Branch.promotedBuilds(vararg promoted: Boolean): List<Build> {
        val level = goldLevel()
        return promoted.map { isPromoted ->
            build().apply { if (isPromoted) promote(level) }
        }
    }

    private val gold = StandardBuildFilterData.of(10).withWithPromotionLevel("GOLD")

    @Test
    fun `The first match in build order, not the last one`() {
        project {
            branch {
                val builds = promotedBuilds(false, true, true)
                assertEquals(builds[1].id, first(gold, builds[0].bound())?.id)
            }
        }
    }

    @Test
    fun `The lower bound is inclusive`() {
        project {
            branch {
                val builds = promotedBuilds(true, true)
                assertEquals(builds[1].id, first(gold, builds[1].bound())?.id)
            }
        }
    }

    @Test
    fun `A match older than the lower bound does not count, even promoted after it`() {
        project {
            branch {
                val builds = promotedBuilds(false, false)
                builds[0].promote(goldLevel())
                assertNull(first(gold, builds[1].bound()))
            }
        }
    }

    @Test
    fun `The upper bound is exclusive`() {
        project {
            branch {
                val builds = promotedBuilds(false, false, true)
                assertNull(first(gold, builds[0].bound(), builds[2].bound()))
                val more = promotedBuilds(false)
                assertEquals(builds[2].id, first(gold, builds[0].bound(), more[0].bound())?.id)
            }
        }
    }

    @Test
    fun `The bounds can be times, on the creation of the builds`() {
        project {
            branch {
                val base = Time.now.minusDays(10)
                val level = goldLevel()
                val builds = (0..3).map { index ->
                    build {
                        updateBuildSignature(time = base.plusDays(index.toLong()))
                        promote(level)
                    }
                }
                // Inclusive
                assertEquals(
                    builds[1].id,
                    first(gold, BuildBound.ByTime(base.plusDays(1)))?.id,
                )
                // Exclusive
                assertEquals(
                    builds[1].id,
                    first(gold, BuildBound.ByTime(base.plusHours(1)), BuildBound.ByTime(base.plusDays(2)))?.id,
                )
                assertNull(
                    first(gold, BuildBound.ByTime(base.plusHours(1)), BuildBound.ByTime(base.plusDays(1)))
                )
            }
        }
    }

    @Test
    fun `The count is ignored, whatever it is`() {
        project {
            branch {
                val builds = promotedBuilds(false, false, false, true)
                val filter = StandardBuildFilterData.of(1).withWithPromotionLevel("GOLD")
                assertEquals(builds[3].id, first(filter, builds[0].bound())?.id)
                assertEquals(
                    builds[3].id,
                    first(filter.copy(count = 0), builds[0].bound())?.id,
                )
            }
        }
    }

    @Test
    fun `The since criteria are left out of the query`() {
        project {
            branch {
                val builds = promotedBuilds(true, false, false)
                // The head of the branch would keep the last GOLD and what follows, whatever the fix
                val later = promotedBuilds(true)
                val filter = gold.withSincePromotionLevel("GOLD")
                assertEquals(builds[0].id, first(filter, builds[0].bound())?.id)
                assertEquals(later[0].id, first(filter, builds[1].bound())?.id)
                // A stamp which does not exist would refuse the query
                assertEquals(
                    builds[0].id,
                    first(gold.withSinceValidationStamp("NOT-THERE"), builds[0].bound())?.id,
                )
            }
        }
    }

    @Test
    fun `A link to several builds gives one match, the first one`() {
        project {
            val target = project()
            val targetBranch = target.branch()
            val targets = (1..2).map { targetBranch.build() }
            branch {
                val linked = build()
                targets.forEach { linked.linkTo(it) }
                val filter = StandardBuildFilterData.of(10).withLinkedTo("${target.name}:*")
                assertEquals(linked.id, first(filter, linked.bound())?.id)
            }
        }
    }

    @Test
    fun `An invalid regular expression is refused`() {
        project {
            branch {
                val builds = promotedBuilds(true)
                listOf("[", """\p{L}+""").forEach { pattern ->
                    assertFailsWith<CoreBuildFilterInvalidException>(pattern) {
                        first(StandardBuildFilterData.of(10).withWithDisplayName(pattern), builds[0].bound())
                    }
                }
            }
        }
    }
}
