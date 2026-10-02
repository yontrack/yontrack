package net.nemerosa.ontrack.service

import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.structure.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The status filters on validation runs match the **current** status of a run — the last row of
 * its status history — and never one of its past statuses (#1712).
 */
@AsAdminTest
class ValidationRunLastStatusFilterIT : AbstractDSLTestSupport() {

    private val passedStatuses = listOf(
        ValidationRunStatusID.STATUS_PASSED,
        ValidationRunStatusID.STATUS_FIXED,
    )

    private val notPassedStatuses = listOf(
        ValidationRunStatusID.STATUS_FAILED,
        ValidationRunStatusID.STATUS_DEFECTIVE,
        ValidationRunStatusID.STATUS_EXPLAINED,
        ValidationRunStatusID.STATUS_INVESTIGATING,
        ValidationRunStatusID.STATUS_INTERRUPTED,
        ValidationRunStatusID.STATUS_WARNING,
    )

    /**
     * Runs whose history matters, on one validation stamp:
     *
     * ```
     * build 1   a1  FAILED -> PASSED     (passed now)
     * build 2   b2  PASSED -> DEFECTIVE  (not passed now)
     * build 3   c3  PASSED -> FIXED      (passed now, two passed rows)
     *           a3  FAILED -> PASSED     (passed now)
     *           b3  PASSED -> DEFECTIVE  (not passed now)
     *           e3  FAILED               (not passed now)
     * ```
     */
    private class Fixture(
        val branch: Branch,
        val vs: ValidationStamp,
        val build3: Build,
        val a1: ValidationRun,
        val b2: ValidationRun,
        val c3: ValidationRun,
        val a3: ValidationRun,
        val b3: ValidationRun,
        val e3: ValidationRun,
    )

    private fun fixture(code: Fixture.() -> Unit) {
        project {
            branch {
                val vs = validationStamp()
                lateinit var a1: ValidationRun
                lateinit var b2: ValidationRun
                lateinit var c3: ValidationRun
                lateinit var a3: ValidationRun
                lateinit var b3: ValidationRun
                lateinit var e3: ValidationRun
                build("1") {
                    a1 = validate(vs, ValidationRunStatusID.STATUS_FAILED)
                        .forceStatusHistory(ValidationRunStatusID.STATUS_PASSED)
                }
                build("2") {
                    b2 = validate(vs, ValidationRunStatusID.STATUS_PASSED)
                        .forceStatusHistory(ValidationRunStatusID.STATUS_DEFECTIVE)
                }
                val build3 = build("3") {
                    c3 = validate(vs, ValidationRunStatusID.STATUS_PASSED)
                        .forceStatusHistory(ValidationRunStatusID.STATUS_FIXED)
                    a3 = validate(vs, ValidationRunStatusID.STATUS_FAILED)
                        .forceStatusHistory(ValidationRunStatusID.STATUS_PASSED)
                    b3 = validate(vs, ValidationRunStatusID.STATUS_PASSED)
                        .forceStatusHistory(ValidationRunStatusID.STATUS_DEFECTIVE)
                    e3 = validate(vs, ValidationRunStatusID.STATUS_FAILED)
                }
                Fixture(this, vs, build3, a1, b2, c3, a3, b3, e3).code()
            }
        }
    }

    private fun List<ValidationRun>.ids() = map { it.id() }

    @Test
    fun `Validation stamp runs filtered on passed statuses`() {
        fixture {
            assertEquals(
                listOf(a3, c3, a1).ids(),
                structureService.getValidationRunsForValidationStampAndStatus(vs, passedStatuses, 0, 10).ids()
            )
        }
    }

    @Test
    fun `Validation stamp runs filtered on not passed statuses`() {
        fixture {
            assertEquals(
                listOf(e3, b3, b2).ids(),
                structureService.getValidationRunsForValidationStampAndStatus(vs, notPassedStatuses, 0, 10).ids()
            )
        }
    }

    @Test
    fun `Validation stamp runs filtered on statuses are paged on distinct runs`() {
        fixture {
            assertEquals(
                listOf(a3, c3).ids(),
                structureService.getValidationRunsForValidationStampAndStatus(vs, passedStatuses, 0, 2).ids()
            )
            assertEquals(
                listOf(a1).ids(),
                structureService.getValidationRunsForValidationStampAndStatus(vs, passedStatuses, 2, 2).ids()
            )
            assertEquals(
                listOf(e3, b3).ids(),
                structureService.getValidationRunsForValidationStampAndStatus(vs, notPassedStatuses, 0, 2).ids()
            )
            assertEquals(
                listOf(b2).ids(),
                structureService.getValidationRunsForValidationStampAndStatus(vs, notPassedStatuses, 2, 2).ids()
            )
        }
    }

    @Test
    fun `Validation stamp runs count filtered on statuses`() {
        fixture {
            assertEquals(3, structureService.getValidationRunsCountForValidationStampAndStatus(vs.id, passedStatuses))
            assertEquals(3, structureService.getValidationRunsCountForValidationStampAndStatus(vs.id, notPassedStatuses))
            // A status only held in the past is not counted
            val passedAndDefective = listOf(ValidationRunStatusID.STATUS_PASSED, ValidationRunStatusID.STATUS_DEFECTIVE)
            assertEquals(
                listOf(b3, a3, b2, a1).ids(),
                structureService.getValidationRunsForValidationStampAndStatus(vs, passedAndDefective, 0, 10).ids()
            )
            assertEquals(4, structureService.getValidationRunsCountForValidationStampAndStatus(vs.id, passedAndDefective))
        }
    }

    @Test
    fun `Build and validation stamp runs filtered on statuses`() {
        fixture {
            assertEquals(
                listOf(a3, c3).ids(),
                structureService.getValidationRunsForBuildAndValidationStampAndStatus(
                    build3.id, vs.id, passedStatuses, 0, 10
                ).ids()
            )
            assertEquals(
                listOf(e3, b3).ids(),
                structureService.getValidationRunsForBuildAndValidationStampAndStatus(
                    build3.id, vs.id, notPassedStatuses, 0, 10
                ).ids()
            )
            // Paging on distinct runs
            assertEquals(
                listOf(a3).ids(),
                structureService.getValidationRunsForBuildAndValidationStampAndStatus(
                    build3.id, vs.id, passedStatuses, 0, 1
                ).ids()
            )
            assertEquals(
                listOf(c3).ids(),
                structureService.getValidationRunsForBuildAndValidationStampAndStatus(
                    build3.id, vs.id, passedStatuses, 1, 1
                ).ids()
            )
            // Counter agrees with the list
            assertEquals(
                2,
                structureService.getValidationRunsCountForBuildAndValidationStamp(
                    build3.id, vs.id, passedStatuses.map { it.id }
                )
            )
            assertEquals(
                2,
                structureService.getValidationRunsCountForBuildAndValidationStamp(
                    build3.id, vs.id, notPassedStatuses.map { it.id }
                )
            )
        }
    }

    @Test
    fun `Branch runs filtered on statuses`() {
        fixture {
            assertEquals(
                listOf(a3, c3, a1).ids(),
                structureService.getValidationRunsForStatus(branch.id, passedStatuses, 0, 10).ids()
            )
            assertEquals(
                listOf(e3, b3, b2).ids(),
                structureService.getValidationRunsForStatus(branch.id, notPassedStatuses, 0, 10).ids()
            )
            // Paging on distinct runs
            assertEquals(
                listOf(a3, c3).ids(),
                structureService.getValidationRunsForStatus(branch.id, passedStatuses, 0, 2).ids()
            )
            assertEquals(
                listOf(a1).ids(),
                structureService.getValidationRunsForStatus(branch.id, passedStatuses, 2, 2).ids()
            )
        }
    }

    /**
     * ```
     * build 1   PASSED
     * build 2   PASSED -> DEFECTIVE
     * build 3   FAILED -> PASSED  (on another stamp only)
     * build 4
     * ```
     *
     * Since the last build _currently_ passed on the stamp: build 1, not build 2.
     */
    @Test
    fun `Standard build filter since a validation stamp status anchors on the current status`() {
        project {
            branch {
                val vs = validationStamp()
                val other = validationStamp()
                build("1") {
                    validate(vs, ValidationRunStatusID.STATUS_PASSED)
                }
                build("2") {
                    validate(vs, ValidationRunStatusID.STATUS_PASSED)
                        .forceStatusHistory(ValidationRunStatusID.STATUS_DEFECTIVE)
                }
                build("3") {
                    validate(other, ValidationRunStatusID.STATUS_PASSED)
                }
                build("4")

                val passed = buildFilterService.standardFilterProviderData(10)
                    .withSinceValidationStamp(vs.name)
                    .withSinceValidationStampStatus(ValidationRunStatusID.PASSED)
                    .build()
                    .filterBranchBuilds(this)
                assertEquals(listOf("4", "3", "2", "1"), passed.map { it.name })

                val defective = buildFilterService.standardFilterProviderData(10)
                    .withSinceValidationStamp(vs.name)
                    .withSinceValidationStampStatus(ValidationRunStatusID.DEFECTIVE)
                    .build()
                    .filterBranchBuilds(this)
                assertEquals(listOf("4", "3", "2"), defective.map { it.name })
            }
        }
    }

    /**
     * ```
     * build 1   FAILED
     * build 2   FAILED -> PASSED
     * build 3
     * ```
     *
     * Since the last build _currently_ failed on the stamp: build 1, not build 2.
     */
    @Test
    fun `Standard build filter since a validation stamp status ignores a past status`() {
        project {
            branch {
                val vs = validationStamp()
                build("1") {
                    validate(vs, ValidationRunStatusID.STATUS_FAILED)
                }
                build("2") {
                    validate(vs, ValidationRunStatusID.STATUS_FAILED)
                        .forceStatusHistory(ValidationRunStatusID.STATUS_PASSED)
                }
                build("3")

                val failed = buildFilterService.standardFilterProviderData(10)
                    .withSinceValidationStamp(vs.name)
                    .withSinceValidationStampStatus(ValidationRunStatusID.FAILED)
                    .build()
                    .filterBranchBuilds(this)
                assertEquals(listOf("3", "2", "1"), failed.map { it.name })
            }
        }
    }

}
