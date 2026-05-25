package net.nemerosa.ontrack.extension.av.event

import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEvents.AUTO_VERSIONING_ERROR
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEvents.AUTO_VERSIONING_POST_PROCESSING_ERROR
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEvents.AUTO_VERSIONING_PR_MERGE_TIMEOUT_ERROR
import net.nemerosa.ontrack.extension.av.event.AutoVersioningEvents.AUTO_VERSIONING_SUCCESS
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequest
import net.nemerosa.ontrack.model.events.EventFactory
import net.nemerosa.ontrack.model.events.EventPostService
import net.nemerosa.ontrack.model.support.StartupService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AutoVersioningEventServiceImpl(
    private val eventFactory: EventFactory,
    private val eventPostService: EventPostService,
    private val autoVersioningEventsFactory: AutoVersioningEventsFactory,
) : AutoVersioningEventService, StartupService {

    /**
     * We do need a separate transaction to send events in case of error
     * because the current transaction WILL be cancelled
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun sendError(order: AutoVersioningOrder, message: String, error: Exception) {
        eventPostService.post(
            autoVersioningEventsFactory.error(order, message, error)
        )
    }

    override fun sendPRMergeTimeoutError(order: AutoVersioningOrder, pr: SCMPullRequest) {
        eventPostService.post(
            autoVersioningEventsFactory.prMergeTimeoutError(order, pr)
        )
    }

    override fun sendSuccess(order: AutoVersioningOrder, message: String, pr: SCMPullRequest) {
        eventPostService.post(
            autoVersioningEventsFactory.success(order, message, pr)
        )
    }

    override fun sendSuccessPush(
        order: AutoVersioningOrder,
        message: String,
        commit: String,
        commitLink: String,
    ) {
        eventPostService.post(
            autoVersioningEventsFactory.success(order, message, commit = commit, commitLink = commitLink)
        )
    }

    override fun getName(): String = "Registration of auto versioning events"

    override fun startupOrder(): Int = StartupService.JOB_REGISTRATION

    override fun start() {
        eventFactory.register(AUTO_VERSIONING_SUCCESS)
        eventFactory.register(AUTO_VERSIONING_ERROR)
        eventFactory.register(AUTO_VERSIONING_POST_PROCESSING_ERROR)
        eventFactory.register(AUTO_VERSIONING_PR_MERGE_TIMEOUT_ERROR)
    }

}