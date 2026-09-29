package net.nemerosa.ontrack.extension.hook.queue

import net.nemerosa.ontrack.extension.hook.HookInfoLinkExtension
import net.nemerosa.ontrack.extension.hook.HookResponse
import net.nemerosa.ontrack.extension.hook.HookResponseType
import net.nemerosa.ontrack.extension.hook.createHookInfoLink
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatchResult
import net.nemerosa.ontrack.extension.queue.dispatching.QueueDispatchResultType

/**
 * Converts a list of queuing dispatching results to a hook response.
 */
fun <T> List<QueueDispatchResult>.toHookResponse(
        extension: HookInfoLinkExtension<List<QueueDispatchResult>>,
): HookResponse {

    val type: HookResponseType
    val types = map { it.type }
    if (types.all { it == QueueDispatchResultType.PROCESSED }) {
        type = HookResponseType.PROCESSED
    } else if (types.all { it == QueueDispatchResultType.IGNORED }) {
        type = HookResponseType.IGNORED
    } else {
        type = HookResponseType.PROCESSING
    }

    return HookResponse(
            type = type,
            infoLink = extension.createHookInfoLink(this),
    )
}