package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.model.structure.PromotionRun
import net.nemerosa.ontrack.model.structure.Signature
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * Entries of the promotions of a build.
 */
class PromotionTrailListenerIT : AbstractTrailListenerITSupport() {

    @Test
    fun `Promoting a build writes promotion added, its signature claimed`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel("GOLD")
                    build {
                        val claimedTime = LocalDateTime.of(2026, 9, 30, 16, 0, 0, 999_000_000)
                        val (run, actor) = asCi {
                            structureService.newPromotionRun(
                                PromotionRun.of(this, pl, Signature.of(claimedTime, "release-manager"), "Go")
                            )
                        }
                        trailAfter(1).assertSingleEntry(
                            TrailEntryTypes.PROMOTION_ADDED,
                            mapOf(
                                "promotionLevel" to mapOf("id" to pl.id(), "name" to "GOLD"),
                                "promotionRun" to mapOf("id" to run.id()),
                                "description" to "Go",
                                "claimed" to claimed(claimedTime, "release-manager"),
                            ),
                            actor,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `Deleting a promotion run writes promotion removed`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel("GOLD")
                    build {
                        val run = promote(pl)
                        val seq = lastSeq()
                        val (_, actor) = asCi {
                            structureService.deletePromotionRun(run.id)
                        }
                        trailAfter(seq).assertSingleEntry(
                            TrailEntryTypes.PROMOTION_REMOVED,
                            mapOf(
                                "promotionLevel" to mapOf("id" to pl.id(), "name" to "GOLD"),
                                "promotionRun" to mapOf("id" to run.id()),
                            ),
                            actor,
                        )
                    }
                }
            }
        }
    }
}
