package net.nemerosa.ontrack.extension.av.event

import net.nemerosa.ontrack.extension.av.dispatcher.AutoVersioningOrder
import net.nemerosa.ontrack.extension.scm.service.SCMPullRequest
import net.nemerosa.ontrack.model.events.Event

interface AutoVersioningEventsFactory {

    fun success(
        order: AutoVersioningOrder,
        message: String,
        pr: SCMPullRequest? = null,
        commit: String? = null,
        commitLink: String? = null,
    ): Event

    fun error(
        order: AutoVersioningOrder,
        message: String,
        error: Exception,
    ): Event

    fun prMergeTimeoutError(
        order: AutoVersioningOrder,
        pr: SCMPullRequest,
    ): Event
    
}