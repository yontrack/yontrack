package net.nemerosa.ontrack.extension.audittrail.listener

import net.nemerosa.ontrack.extension.audittrail.model.TrailEntryTypes
import net.nemerosa.ontrack.model.structure.Build
import net.nemerosa.ontrack.model.structure.ValidationRunStatusID
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Entries written on the builds which a deletion reaches beyond the deleted entity: the runs of a
 * deleted validation stamp or promotion level, the links other builds hold to a deleted build.
 */
class CascadeTrailListenerIT : AbstractTrailListenerITSupport() {

    @Test
    fun `Deleting a validation stamp writes validation deleted on every build which had a run of it`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("unit-tests")
                    val other = validationStamp("lint")
                    val build1 = build()
                    val run11 = build1.validate(vs, ValidationRunStatusID.STATUS_FAILED)
                    val run12 = build1.validate(vs, ValidationRunStatusID.STATUS_PASSED)
                    build1.validate(other)
                    val build2 = build()
                    val run21 = build2.validate(vs, ValidationRunStatusID.STATUS_WARNING)
                    // Build without any run of the stamp
                    val build3 = build()
                    build3.validate(other)
                    val seq1 = build1.lastSeq()
                    val seq2 = build2.lastSeq()
                    val seq3 = build3.lastSeq()

                    val (_, actor) = asCi {
                        structureService.deleteValidationStamp(vs.id)
                    }

                    val stamp = mapOf("id" to vs.id(), "name" to "unit-tests")
                    val reason = "cascade/validation-stamp-deleted"
                    build1.trailAfter(seq1).let { entries ->
                        assertEquals(2, entries.size)
                        entries[0].assertEntry(
                            TrailEntryTypes.VALIDATION_DELETED,
                            mapOf(
                                "validationStamp" to stamp,
                                "validationRun" to mapOf("id" to run11.id(), "order" to 1),
                                "status" to "FAILED",
                                "reason" to reason,
                            ),
                            actor,
                        )
                        entries[1].assertEntry(
                            TrailEntryTypes.VALIDATION_DELETED,
                            mapOf(
                                "validationStamp" to stamp,
                                "validationRun" to mapOf("id" to run12.id(), "order" to 2),
                                "status" to "PASSED",
                                "reason" to reason,
                            ),
                            actor,
                        )
                    }
                    build2.trailAfter(seq2).assertSingleEntry(
                        TrailEntryTypes.VALIDATION_DELETED,
                        mapOf(
                            "validationStamp" to stamp,
                            "validationRun" to mapOf("id" to run21.id(), "order" to 1),
                            "status" to "WARNING",
                            "reason" to reason,
                        ),
                        actor,
                    )
                    assertEquals(emptyList(), build3.trailAfter(seq3), "Nothing written on a build without a run of the stamp")
                }
            }
        }
    }

    @Test
    fun `Deleting a promotion level writes promotion removed on every build promoted to it`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel("GOLD")
                    val other = promotionLevel("SILVER")
                    val build1 = build()
                    val run1 = build1.promote(pl)
                    build1.promote(other)
                    val build2 = build()
                    val run2 = build2.promote(pl)
                    val build3 = build()
                    build3.promote(other)
                    val seq1 = build1.lastSeq()
                    val seq2 = build2.lastSeq()
                    val seq3 = build3.lastSeq()

                    val (_, actor) = asCi {
                        structureService.deletePromotionLevel(pl.id)
                    }

                    val level = mapOf("id" to pl.id(), "name" to "GOLD")
                    val reason = "cascade/promotion-level-deleted"
                    build1.trailAfter(seq1).assertSingleEntry(
                        TrailEntryTypes.PROMOTION_REMOVED,
                        mapOf("promotionLevel" to level, "promotionRun" to mapOf("id" to run1.id()), "reason" to reason),
                        actor,
                    )
                    build2.trailAfter(seq2).assertSingleEntry(
                        TrailEntryTypes.PROMOTION_REMOVED,
                        mapOf("promotionLevel" to level, "promotionRun" to mapOf("id" to run2.id()), "reason" to reason),
                        actor,
                    )
                    assertEquals(emptyList(), build3.trailAfter(seq3), "Nothing written on a build not promoted to the level")
                }
            }
        }
    }

    @Test
    fun `Deleting a build writes link removed on every build linking to it, whatever its branch`() {
        asAdmin {
            val target = project<Build> { branch<Build> { build() } }
            project {
                branch {
                    val source1 = build()
                    source1.linkTo(target, "dependency")
                    source1.linkTo(target)
                    val source2 = build()
                    source2.linkTo(target)
                    // A build which the target links to is not reached
                    val dependency = build()
                    target.linkTo(dependency)
                    val seq1 = source1.lastSeq()
                    val seq2 = source2.lastSeq()
                    val seqDependency = dependency.lastSeq()

                    val (_, actor) = asCi {
                        structureService.deleteBuild(target.id)
                    }

                    val reason = "cascade/target-build-deleted"
                    source1.trailAfter(seq1).let { entries ->
                        assertEquals(2, entries.size)
                        entries[0].assertEntry(
                            TrailEntryTypes.LINK_REMOVED,
                            mapOf("target" to target.ref(), "qualifier" to "", "reason" to reason),
                            actor,
                        )
                        entries[1].assertEntry(
                            TrailEntryTypes.LINK_REMOVED,
                            mapOf("target" to target.ref(), "qualifier" to "dependency", "reason" to reason),
                            actor,
                        )
                    }
                    source2.trailAfter(seq2).assertSingleEntry(
                        TrailEntryTypes.LINK_REMOVED,
                        mapOf("target" to target.ref(), "qualifier" to "", "reason" to reason),
                        actor,
                    )
                    assertEquals(emptyList(), dependency.trailAfter(seqDependency), "Nothing written on a build the deleted one links to")
                }
            }
        }
    }

    @Test
    fun `A cascade on a build without a trail opens it first`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("unit-tests")
                    val run = untrailedBuild { withoutTrail { validate(vs) } }
                    structureService.deleteValidationStamp(vs.id)
                    assertEquals(
                        listOf(TrailEntryTypes.TRAIL_OPENED, TrailEntryTypes.VALIDATION_DELETED),
                        run.build.trail().map { it.type },
                    )
                }
            }
        }
    }

    @Test
    fun `No cascade entry is written while the licence is off`() {
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("unit-tests")
                    val build = build()
                    build.validate(vs)
                    val seq = build.lastSeq()
                    withoutTrail { structureService.deleteValidationStamp(vs.id) }
                    assertEquals(emptyList(), build.trailAfter(seq))
                }
            }
        }
    }

    /**
     * Not a benchmark: keeps the cost of a large cascade visible in the test output.
     */
    @Test
    fun `Deleting a validation stamp with a few thousand runs`() {
        val buildCount = 50
        val runsPerBuild = 40
        asAdmin {
            project {
                branch {
                    val vs = validationStamp("unit-tests")
                    val builds = withoutTrail {
                        (1..buildCount).map {
                            build().apply { repeat(runsPerBuild) { validate(vs) } }
                        }
                    }

                    val start = System.nanoTime()
                    structureService.deleteValidationStamp(vs.id)
                    val elapsedMs = (System.nanoTime() - start) / 1_000_000
                    println("[audit-trail] Deleting a validation stamp with ${buildCount * runsPerBuild} runs on $buildCount builds: $elapsedMs ms")

                    builds.forEach { build ->
                        val entries = build.trail()
                        assertEquals(1 + runsPerBuild, entries.size)
                        assertEquals(TrailEntryTypes.TRAIL_OPENED, entries.first().type)
                        assertTrue(entries.drop(1).all { it.type == TrailEntryTypes.VALIDATION_DELETED })
                        assertEquals((1..runsPerBuild).toList(), entries.drop(1).map { it.payload.path("validationRun").path("order").asInt() })
                    }
                    assertTrue(elapsedMs < 60_000, "Cascade of ${buildCount * runsPerBuild} runs under a minute")
                }
            }
        }
    }
}
