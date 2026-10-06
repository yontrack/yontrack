package net.nemerosa.ontrack.extension.av.event

import net.nemerosa.ontrack.common.Time
import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.scm.changelog.SimpleSCMCommit
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequest
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequestStatus
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.HtmlNotificationEventRenderer
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.PromotionRun
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@AsAdminTest
internal class AutoVersioningEventsIT : AbstractDSLTestSupport() {

    @Autowired
    private lateinit var htmlNotificationEventRenderer: HtmlNotificationEventRenderer

    @Autowired
    private lateinit var eventTemplatingService: EventTemplatingService

    @Autowired
    private lateinit var autoVersioningEventsFactory: AutoVersioningEventsFactory

    @Test
    fun `Rendering the post processing error event`() {
        withOrder { order, run, target ->
            val event = autoVersioningEventsFactory.error(
                order = order,
                message = "Post processing error",
                error = MockPostProcessingFailureException(
                    message = "Remote job failed",
                    link = "https://job.link",
                )
            )
            assertAuditValues(event, order)
            val text = eventTemplatingService.renderEvent(
                event,
                context = emptyMap(),
                renderer = htmlNotificationEventRenderer
            )
            assertEquals(
                """
                    Auto versioning post-processing of <a href="http://localhost:3000/project/${target.project.id}">${target.project.name}</a>/<a href="http://localhost:3000/branch/${target.id}">${target.name}</a> for dependency <a href="http://localhost:3000/project/${run.project.id}">${run.project.name}</a> version "1.1.0" has failed.

                    <a href="https://job.link">Post processing error.</a>

                    ${auditLink(order)}
                """.trimIndent(),
                text
            )
        }
    }

    @Test
    fun `Rendering the success event`() {
        withOrder { order, run, target ->
            val event = autoVersioningEventsFactory.success(
                order = order,
                message = "Created, approved and merged.",
                pr = SCMPullRequest(
                    id = "42",
                    name = "PR-42",
                    link = "https://scm/pr/42",
                    status = SCMPullRequestStatus.MERGED,
                )
            )
            assertAuditValues(event, order)
            assertEquals("Pull request PR-42", event.getValue("CHANGE_NAME"))
            assertEquals("https://scm/pr/42", event.getValue("CHANGE_LINK"))
            assertEquals("PR-42", event.getValue("PR_NAME"))
            assertEquals("https://scm/pr/42", event.getValue("PR_LINK"))
            assertNull(event.values["COMMIT"], "No commit for a PR")
            assertNull(event.values["COMMIT_LINK"], "No commit link for a PR")
            val text = eventTemplatingService.renderEvent(
                event,
                context = emptyMap(),
                renderer = htmlNotificationEventRenderer
            )
            assertEquals(
                """
                            Auto versioning of <a href="http://localhost:3000/project/${target.project.id}">${target.project.name}</a>/<a href="http://localhost:3000/branch/${target.id}">${target.name}</a> for dependency <a href="http://localhost:3000/project/${run.project.id}">${run.project.name}</a> version "1.1.0" has been done.

                            Created, approved and merged.

                            <a href="https://scm/pr/42">Pull request PR-42</a>

                            ${auditLink(order)}
                        """.trimIndent(),
                text
            )
        }
    }

    @Test
    fun `Rendering the success event for a direct push`() {
        withOrder { order, run, target ->
            val event = autoVersioningEventsFactory.success(
                order = order,
                message = "Auto-versioning pushed.",
                commit = SimpleSCMCommit(
                    id = "abc1234def5678",
                    shortId = "abc1234",
                    author = "test",
                    authorEmail = null,
                    timestamp = Time.now(),
                    message = "Auto-versioning",
                    link = "https://scm/commit/abc1234def5678",
                ),
            )
            assertAuditValues(event, order)
            assertEquals("Commit abc1234", event.getValue("CHANGE_NAME"))
            assertEquals("https://scm/commit/abc1234def5678", event.getValue("CHANGE_LINK"))
            assertEquals("abc1234def5678", event.getValue("COMMIT"))
            assertEquals("https://scm/commit/abc1234def5678", event.getValue("COMMIT_LINK"))
            assertNull(event.values["PR_NAME"], "No PR for a direct push")
            assertNull(event.values["PR_LINK"], "No PR link for a direct push")
            val text = eventTemplatingService.renderEvent(
                event,
                context = emptyMap(),
                renderer = htmlNotificationEventRenderer
            )
            assertFalse("Pull request" in text, "A direct push does not mention a pull request: $text")
            assertEquals(
                """
                            Auto versioning of <a href="http://localhost:3000/project/${target.project.id}">${target.project.name}</a>/<a href="http://localhost:3000/branch/${target.id}">${target.name}</a> for dependency <a href="http://localhost:3000/project/${run.project.id}">${run.project.name}</a> version "1.1.0" has been done.

                            Auto-versioning pushed.

                            <a href="https://scm/commit/abc1234def5678">Commit abc1234</a>

                            ${auditLink(order)}
                        """.trimIndent(),
                text
            )
        }
    }

    @Test
    fun `Rendering the processing error event`() {
        withOrder { order, run, target ->
            val event = autoVersioningEventsFactory.error(
                order = order,
                message = "Processing failed.",
                error = RuntimeException("Processing failed because of this error.")
            )
            assertAuditValues(event, order)
            val text = eventTemplatingService.renderEvent(
                event,
                context = emptyMap(),
                renderer = htmlNotificationEventRenderer
            )
            assertEquals(
                """
                            Auto versioning of <a href="http://localhost:3000/project/${target.project.id}">${target.project.name}</a>/<a href="http://localhost:3000/branch/${target.id}">${target.name}</a> for dependency <a href="http://localhost:3000/project/${run.project.id}">${run.project.name}</a> version "1.1.0" has failed.
    
                            Processing failed.
                            
                            Error: Processing failed because of this error.

                            ${auditLink(order)}
                        """.trimIndent(),
                text
            )
        }
    }

    @Test
    fun `Rendering the PR timeout event`() {
        withOrder { order, run, target ->
            val event = autoVersioningEventsFactory.prMergeTimeoutError(
                order = order,
                pr = SCMPullRequest(
                    id = "42",
                    name = "PR-42",
                    link = "https://scm/pr/42",
                    status = SCMPullRequestStatus.OPEN,
                )
            )
            assertAuditValues(event, order)
            val text = eventTemplatingService.renderEvent(
                event,
                context = emptyMap(),
                renderer = htmlNotificationEventRenderer
            )
            assertEquals(
                """
                    Auto versioning of <a href="http://localhost:3000/project/${target.project.id}">${target.project.name}</a>/<a href="http://localhost:3000/branch/${target.id}">${target.name}</a> for dependency <a href="http://localhost:3000/project/${run.project.id}">${run.project.name}</a> version "1.1.0" has failed.

                    Timeout while waiting for the PR to be ready to be merged.
                    
                    Pull request <a href="https://scm/pr/42">PR-42</a>

                    ${auditLink(order)}
                """.trimIndent(),
                text
            )
        }
    }

    @Test
    fun `Rendering the rejected event`() {
        withOrder { order, run, target ->
            val event = autoVersioningEventsFactory.rejected(
                order = order,
                reason = "Version \"1.1.0\" is older than the version \"2.0.0\" already present in the target file.",
            )
            assertAuditValues(event, order)
            val text = eventTemplatingService.renderEvent(
                event,
                context = emptyMap(),
                renderer = htmlNotificationEventRenderer
            )
            assertEquals(
                """
                    Auto versioning of <a href="http://localhost:3000/project/${target.project.id}">${target.project.name}</a>/<a href="http://localhost:3000/branch/${target.id}">${target.name}</a> for dependency <a href="http://localhost:3000/project/${run.project.id}">${run.project.name}</a> version "1.1.0" has been rejected.

                    Version "1.1.0" is older than the version "2.0.0" already present in the target file.

                    ${auditLink(order)}
                """.trimIndent(),
                text
            )
        }
    }

    private fun auditUrl(order: AutoVersioningOrder) =
        "http://localhost:3000/extension/auto-versioning/audit/detail/${order.uuid}"

    private fun auditLink(order: AutoVersioningOrder) =
        """<a href="${auditUrl(order)}">Auto-versioning audit</a>"""

    private fun assertAuditValues(event: Event, order: AutoVersioningOrder) {
        assertEquals(auditUrl(order), event.getValue("AUDIT_LINK"))
        assertEquals("Auto-versioning audit", event.getValue("AUDIT_NAME"))
    }

    private fun withOrder(
        code: (order: AutoVersioningOrder, run: PromotionRun, target: Branch) -> Unit
    ) {
        val target = doCreateBranch()
        project {
            branch {
                val pl = promotionLevel()
                build {
                    val run = promote(pl)
                    val order = createOrder(
                        targetBranch = target,
                        targetVersion = "1.1.0",
                        sourcePromotionRunId = run.id(),
                    )
                    code(order, run, target)
                }
            }
        }
    }

}