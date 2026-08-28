package net.nemerosa.ontrack.extension.av.event

import net.nemerosa.ontrack.extension.av.AutoVersioningTestFixtures.createOrder
import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequest
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequestStatus
import net.nemerosa.ontrack.it.AbstractDSLTestSupport
import net.nemerosa.ontrack.it.AsAdminTest
import net.nemerosa.ontrack.model.events.EventTemplatingService
import net.nemerosa.ontrack.model.events.HtmlNotificationEventRenderer
import net.nemerosa.ontrack.model.structure.Branch
import net.nemerosa.ontrack.model.structure.PromotionRun
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

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
            val text = eventTemplatingService.renderEvent(
                event,
                context = emptyMap(),
                renderer = htmlNotificationEventRenderer
            )
            assertEquals(
                """
                    Auto versioning post-processing of <a href="http://localhost:3000/project/${target.project.id}">${target.project.name}</a>/<a href="http://localhost:3000/branch/${target.id}">${target.name}</a> for dependency <a href="http://localhost:3000/project/${run.project.id}">${run.project.name}</a> version "1.1.0" has failed.

                    <a href="https://job.link">Post processing error.</a>
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
            val text = eventTemplatingService.renderEvent(
                event,
                context = emptyMap(),
                renderer = htmlNotificationEventRenderer
            )
            assertEquals(
                """
                            Auto versioning of <a href="http://localhost:3000/project/${target.project.id}">${target.project.name}</a>/<a href="http://localhost:3000/branch/${target.id}">${target.name}</a> for dependency <a href="http://localhost:3000/project/${run.project.id}">${run.project.name}</a> version "1.1.0" has been done.
    
                            Created, approved and merged.
                            
                            Pull request <a href="https://scm/pr/42">PR-42</a>
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
            val text = eventTemplatingService.renderEvent(
                event,
                context = emptyMap(),
                renderer = htmlNotificationEventRenderer
            )
            assertEquals(
                """
                    Auto versioning of <a href="http://localhost:3000/project/${target.project.id}">${target.project.name}</a>/<a href="http://localhost:3000/branch/${target.id}">${target.name}</a> for dependency <a href="http://localhost:3000/project/${run.project.id}">${run.project.name}</a> version "1.1.0" has been rejected.

                    Version "1.1.0" is older than the version "2.0.0" already present in the target file.
                """.trimIndent(),
                text
            )
        }
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