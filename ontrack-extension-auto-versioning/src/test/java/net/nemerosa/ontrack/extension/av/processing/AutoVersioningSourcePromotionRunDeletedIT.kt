package net.nemerosa.ontrack.extension.av.processing

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.av.AbstractAutoVersioningTestSupport
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditEntry
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryFilter
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditQueryService
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditState
import net.nemerosa.ontrack.extension.av.audit.AutoVersioningAuditStore
import net.nemerosa.ontrack.extension.av.scheduler.AutoVersioningScheduler
import net.nemerosa.ontrack.extension.queue.QueueNoAsync
import net.nemerosa.ontrack.model.events.PlainEventRenderer
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.PromotionRun
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * An auto-versioning order whose source promotion run has been deleted (typically
 * revoked by `autoRevoke`) before its processing is aborted, without touching the
 * target repository.
 *
 * See #2145.
 */
@TestPropertySource(
    properties = [
        // Disabling the scheduling job, so that we can control it explicitly
        "ontrack.extension.auto-versioning.scheduling.enabled=false",
    ]
)
@QueueNoAsync
class AutoVersioningSourcePromotionRunDeletedIT : AbstractAutoVersioningTestSupport() {

    @Autowired
    private lateinit var autoVersioningProcessingService: AutoVersioningProcessingService

    @Autowired
    private lateinit var store: AutoVersioningAuditStore

    @Autowired
    private lateinit var queryService: AutoVersioningAuditQueryService

    @Autowired
    private lateinit var scheduler: AutoVersioningScheduler

    @Autowired
    private lateinit var autoVersioningTemplatingService: AutoVersioningTemplatingService

    @Test
    fun `Order rendering the deleted source promotion run in its PR title is aborted`() {
        withDeletedSourcePromotionRun(
            prTitleTemplate = "Promotion: \${sourcePromotionRun.description}",
        )
    }

    @Test
    fun `Order without any template is aborted when its source promotion run is deleted`() {
        withDeletedSourcePromotionRun(
            prTitleTemplate = null,
        )
    }

    @Test
    fun `Templates can be rendered when the source promotion run has been deleted`() {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    build {
                        val source = this
                        val run = promote(pl)
                        structureService.deletePromotionRun(run.id)
                        project {
                            branch {
                                val order = createOrder(
                                    sourceProject = source.project.name,
                                    sourceBuildId = source.id(),
                                    sourcePromotionRunId = run.id(),
                                    targetVersion = "2.0.0",
                                )
                                val avRenderer = autoVersioningTemplatingService.createAutoVersioningTemplateRenderer(
                                    order = order,
                                    currentVersions = mapOf("gradle.properties" to "1.0.0"),
                                )
                                assertEquals(
                                    "Version 2.0.0",
                                    avRenderer.render("Version \${VERSION}", PlainEventRenderer.INSTANCE),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Scheduled order is aborted when its source promotion run is revoked before the schedule`() {
        withSimpleSetup(
            cronSchedule = "0 0 23 * * *",
            prTitleTemplate = "Promotion: \${sourcePromotionRun.description}",
        ) { pl, target ->
            lateinit var run: PromotionRun
            pl.branch.build("1.0.0") {
                run = promote(pl)
            }
            assertEquals(
                AutoVersioningAuditState.PENDING_SCHEDULE,
                target.auditEntry("1.0.0").mostRecentState.state,
                "Order waiting for its schedule",
            )

            // The promotion is revoked while the order waits
            structureService.deletePromotionRun(run.id)

            // The schedule is reached
            scheduler.schedule(Time.now.plusDays(2))

            target.auditEntry("1.0.0").assertAbortedForDeletedRun()
        }
    }

    private fun withDeletedSourcePromotionRun(prTitleTemplate: String?) {
        asAdmin {
            project {
                branch {
                    val pl = promotionLevel()
                    build {
                        val source = this
                        val run = promote(pl, description = "Revoked promotion")
                        mockSCMTester.withMockSCMRepository {
                            project {
                                branch {
                                    configureMockSCMBranch()
                                    repositoryFile(
                                        path = "gradle.properties",
                                        content = "version = 1.0.0",
                                    )

                                    val order = createOrder(
                                        sourceProject = source.project.name,
                                        sourceBuildId = source.id(),
                                        sourcePromotionRunId = run.id(),
                                        sourcePromotion = pl.name,
                                        targetVersion = "2.0.0",
                                        prTitleTemplate = prTitleTemplate,
                                    )
                                    store.create(order)

                                    // The promotion is revoked before the order is processed
                                    structureService.deletePromotionRun(run.id)

                                    val outcome = autoVersioningProcessingService.process(order)

                                    assertEquals(AutoVersioningProcessingOutcome.SOURCE_REVOKED, outcome)
                                    assertNull(
                                        getRepositoryBranch("feature/*"),
                                        "No upgrade branch created",
                                    )
                                    assertEquals(
                                        "version = 1.0.0",
                                        getRepositoryFile(path = "gradle.properties"),
                                        "Target file unchanged",
                                    )
                                    assertNotNull(
                                        queryService.findByUUID(this, order.uuid),
                                        "Audit entry found",
                                    ) {
                                        it.assertAbortedForDeletedRun()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Branch.auditEntry(version: String): AutoVersioningAuditEntry =
        assertNotNull(
            queryService.findByFilter(
                filter = AutoVersioningAuditQueryFilter(
                    project = project.name,
                    branch = name,
                    version = version,
                )
            ).firstOrNull(),
            "Audit entry for $version found",
        )

    private fun AutoVersioningAuditEntry.assertAbortedForDeletedRun() {
        assertEquals(AutoVersioningAuditState.PROCESSING_ABORTED, mostRecentState.state)
        assertEquals(
            AutoVersioningProcessingServiceImpl.SOURCE_PROMOTION_RUN_DELETED,
            mostRecentState.data["message"],
            "Aborted because of the deleted source promotion run (state data: ${mostRecentState.data})",
        )
    }

}
