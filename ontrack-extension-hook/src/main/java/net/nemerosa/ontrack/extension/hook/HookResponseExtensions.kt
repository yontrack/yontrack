package net.nemerosa.ontrack.extension.hook

/**
 * Response of a disabled hook. The hook record says it is disabled.
 */
fun hookDisabled() = HookResponse(
        type = HookResponseType.IGNORED,
        infoLink = null,
)
