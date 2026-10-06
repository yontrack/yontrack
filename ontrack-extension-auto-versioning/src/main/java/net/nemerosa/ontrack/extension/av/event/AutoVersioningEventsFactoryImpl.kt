package net.nemerosa.ontrack.extension.av.event

import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.postprocessing.PostProcessingFailureException
import net.nemerosa.ontrack.extension.scm.changelog.SCMCommit
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequest
import net.nemerosa.ontrack.model.events.Event
import net.nemerosa.ontrack.model.structure.ID
import net.nemerosa.ontrack.model.structure.StructureService
import net.nemerosa.ontrack.ui.controller.UILocations
import org.springframework.stereotype.Service

@Service
class AutoVersioningEventsFactoryImpl(
    private val structureService: StructureService,
    private val uiLocations: UILocations,
) : AutoVersioningEventsFactory {

    override fun success(
        order: AutoVersioningOrder,
        message: String,
        pr: SCMPullRequest?,
        commit: SCMCommit?,
    ): Event =
        Event.of(AutoVersioningEvents.AUTO_VERSIONING_SUCCESS)
            .withBranch(order.branch)
            .withSourcePromotionRun(order)
            .with("VERSION", order.targetVersion)
            .with("PROMOTION", order.sourcePromotion)
            .with("MESSAGE", close(message))
            .with(
                "CHANGE_NAME",
                pr?.let { "Pull request ${it.name}" } ?: commit?.let { "Commit ${it.shortId}" }
            )
            .with("CHANGE_LINK", pr?.link ?: commit?.link)
            .with("PR_NAME", pr?.name)
            .with("PR_LINK", pr?.link)
            .with("COMMIT", commit?.id)
            .with("COMMIT_LINK", commit?.link)
            .withAudit(order)
            .build()

    override fun error(
        order: AutoVersioningOrder,
        message: String,
        error: Exception,
    ): Event = if (error is PostProcessingFailureException) {
        Event.of(AutoVersioningEvents.AUTO_VERSIONING_POST_PROCESSING_ERROR)
            .withBranch(order.branch)
            .withSourcePromotionRun(order)
            .with("VERSION", order.targetVersion)
            .with("PROMOTION", order.sourcePromotion)
            .with("MESSAGE", close(message))
            .with("LINK", error.link)
            .withAudit(order)
            .build()
    } else {
        Event.of(AutoVersioningEvents.AUTO_VERSIONING_ERROR)
            .withBranch(order.branch)
            .withSourcePromotionRun(order)
            .with("VERSION", order.targetVersion)
            .with("PROMOTION", order.sourcePromotion)
            .with("MESSAGE", close(message))
            .with("ERROR", close(error.message ?: error::class.java.name))
            .withAudit(order)
            .build()
    }

    override fun rejected(
        order: AutoVersioningOrder,
        reason: String,
    ): Event =
        Event.of(AutoVersioningEvents.AUTO_VERSIONING_REJECTED)
            .withBranch(order.branch)
            .withSourcePromotionRun(order)
            .with("VERSION", order.targetVersion)
            .with("PROMOTION", order.sourcePromotion)
            .with("MESSAGE", close(reason))
            .withAudit(order)
            .build()

    override fun prMergeTimeoutError(
        order: AutoVersioningOrder,
        pr: SCMPullRequest,
    ): Event =
        Event.of(AutoVersioningEvents.AUTO_VERSIONING_PR_MERGE_TIMEOUT_ERROR)
            .withBranch(order.branch)
            .withSourcePromotionRun(order)
            .with("VERSION", order.targetVersion)
            .with("PROMOTION", order.sourcePromotion)
            .with("PR_NAME", pr.name)
            .with("PR_LINK", pr.link)
            .withAudit(order)
            .build()

    private fun Event.EventBuilder.withAudit(order: AutoVersioningOrder) =
        with("AUDIT_NAME", "Auto-versioning audit")
            .with("AUDIT_LINK", uiLocations.page("/extension/auto-versioning/audit/detail/${order.uuid}"))

    private fun Event.EventBuilder.withSourcePromotionRun(order: AutoVersioningOrder) =
        sourcePromotionRun(order)?.let {
            withExtra(it)
            withExtra(it.build)
            withExtra(it.build.branch)
            withExtra(it.build.branch.project)
        } ?: this

    private fun sourcePromotionRun(order: AutoVersioningOrder) =
        order.sourcePromotionRunId?.let {
            structureService.findPromotionRunByID(ID.of(it))
        }

    private fun close(message: String) =
        if (message.endsWith(".")) {
            message
        } else {
            "$message."
        }
}