package net.nemerosa.ontrack.extension.scorecard.engine

import net.nemerosa.ontrack.extension.api.support.TestBranchModelMatcherProvider
import net.nemerosa.ontrack.extension.scorecard.model.NoEstateReadingSet
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReadingSubjectResolverIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var readingSubjectResolver: ReadingSubjectResolver

    @Autowired
    private lateinit var testBranchModelMatcherProvider: TestBranchModelMatcherProvider

    @Test
    fun `No branch model, every non-disabled branch is in scope`() {
        asAdmin {
            project {
                branch("main")
                branch("feature-x")
                branch("old") {
                    structureService.disableBranch(this)
                }
                val subject = readingSubjectResolver.resolve(NoEstateReadingSet, this)
                assertEquals(ReadingScopeKind.ALL_BRANCHES, subject.scope.kind)
                assertEquals(listOf("feature-x", "main"), subject.scope.branches.map { it.name })
            }
        }
    }

    @Test
    fun `Branch model, the matched branches are in scope`() {
        asAdmin {
            project {
                testBranchModelMatcherProvider.projects += name
                branch("master")
                branch("release-1.0")
                branch("feature-x")
                val subject = readingSubjectResolver.resolve(NoEstateReadingSet, this)
                assertEquals(ReadingScopeKind.BRANCH_MODEL, subject.scope.kind)
                assertEquals(listOf("master", "release-1.0"), subject.scope.branches.map { it.name })
                assertEquals(
                    mapOf("kind" to "BRANCH_MODEL", "branches" to listOf("master", "release-1.0")),
                    subject.scope.details
                )
            }
        }
    }

    @Test
    fun `Promotion marker with the last level of each branch in scope`() {
        asAdmin {
            project {
                branch("main") {
                    promotionLevel("BRONZE")
                    promotionLevel("SILVER")
                    promotionLevel("GOLD")
                }
                branch("release") {
                    promotionLevel("IRON")
                    promotionLevel("PLATINUM")
                }
                branch("feature") // no level
                val subject = readingSubjectResolver.resolve(NoEstateReadingSet, this)
                assertEquals(MarkerKind.PROMOTION, subject.markerKind)
                val marker = assertIs<PromotionMarker>(subject.marker)
                assertEquals(listOf("GOLD", "PLATINUM"), marker.levels.map { it.name })
                assertEquals(
                    mapOf("levels" to mapOf("main" to "GOLD", "release" to "PLATINUM")),
                    marker.details
                )
            }
        }
    }

    @Test
    fun `No promotion level, no marker`() {
        asAdmin {
            project {
                branch("main")
                val subject = readingSubjectResolver.resolve(NoEstateReadingSet, this)
                assertEquals(MarkerKind.PROMOTION, subject.markerKind)
                assertNull(subject.marker)
            }
        }
    }
}
