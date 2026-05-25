package net.nemerosa.ontrack.extension.av.audit

enum class AutoVersioningAuditState(
    val isRunning: Boolean = true,
    val isProcessing: Boolean = false,
) {

    CREATED,
    SCHEDULED,
    PENDING_SCHEDULE,

    THROTTLED(isRunning = false),

    RECEIVED,
    ERROR(isRunning = false),
    PROCESSING_START(isProcessing = true),
    PROCESSING_ABORTED(isRunning = false),

    PROCESSING_CREATING_BRANCH(isProcessing = true),
    PROCESSING_UPDATING_FILE(isProcessing = true),
    POST_PROCESSING_START(isProcessing = true),
    POST_PROCESSING_LAUNCHED(isProcessing = true),
    POST_PROCESSING_END(isProcessing = true),
    PR_CREATING(isProcessing = true),
    PR_TIMEOUT(isRunning = false),

    /**
     * PR was just created (no auto approval)
     */
    PR_CREATED(isRunning = false),

    /**
     * PR was created and approved, but merge is delegated
     */
    PR_APPROVED(isRunning = false),

    /**
     * PR was merged
     */
    PR_MERGED(isRunning = false),

    /**
     * Pushing to the target branch directly
     */
    PUSHING(isProcessing = true),

    /**
     * Pushed to the target branch directly
     */
    PUSHED(isRunning = false);

    companion object {
        val runningAndNotProcessingStates = entries.filter { it.isRunning && !it.isProcessing }.toSet()
    }

}