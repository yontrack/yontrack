package net.nemerosa.ontrack.extension.environments

enum class SlotPipelineStatus(
    val finished: Boolean,
) {

    CANDIDATE(
        finished = false,
    ),
    RUNNING(
        finished = false
    ),
    CANCELLED(
        finished = true,
    ),
    DONE(
        finished = true
    ),

    /**
     * A deployment which was started and did not make it. Terminal, and reachable from [RUNNING]
     * only - a candidate which never started is cancelled, not failed. It does not change what the
     * slot runs: the last [DONE] pipeline stays the deployed one.
     */
    FAILED(
        finished = true
    );

    companion object {
        val activeStatuses: List<SlotPipelineStatus> = values()
            .filter { !it.finished }
            .toList()
    }
}
