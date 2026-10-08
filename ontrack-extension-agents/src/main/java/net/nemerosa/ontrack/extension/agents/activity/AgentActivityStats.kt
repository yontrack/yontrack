package net.nemerosa.ontrack.extension.agents.activity

import net.nemerosa.ontrack.common.api.APIDescription
import net.nemerosa.ontrack.common.api.APIName
import java.time.LocalDateTime

/**
 * Criteria of the [counts of the agent activity][AgentActivityStats], over the projects the current
 * user can see.
 *
 * @property window Number of days of the window, ending now - clamped to [1, [MAX_WINDOW]]
 * @property projects Names of the projects to narrow the counts to - all the visible projects when empty
 * @property labels Labels the projects must all carry, as `category:name` display strings (`name` for a
 * label without a category) - no restriction when empty
 */
data class AgentActivityStatsFilter(
    val window: Int = DEFAULT_WINDOW,
    val projects: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
) {
    companion object {
        /**
         * Window by default, in days
         */
        const val DEFAULT_WINDOW = 30

        /**
         * Largest window, in days
         */
        const val MAX_WINDOW = 366
    }
}

/**
 * How much of the delivery the agents drive, over a window of days, across the projects the current
 * user can see (#2035).
 *
 * Every count comes from an aggregate query: no event, no build is loaded to count it.
 */
@APIName("AgentActivityStats")
@APIDescription("How much of the delivery the agents drive, over a window of days, across the projects the current user can see.")
data class AgentActivityStats(
    @APIDescription("Number of days of the window, ending now")
    val window: Int,
    @APIDescription("Start of the window (UTC)")
    val from: LocalDateTime,
    @APIDescription("Builds created by agents in the window: `new_build` events whose actor is an agent")
    val builds: Int,
    @APIDescription("Promotions by agents in the window: `new_promotion_run` events whose actor is an agent")
    val promotions: Int,
    @APIDescription("Deployment actions by agents in the window: the slot pipelines started, starting their deployment or deployed (`slot-pipeline-creation`, `slot-pipeline-deploying` and `slot-pipeline-deployed` events) whose actor is an agent")
    val deployments: Int,
    @APIDescription("Builds created in the window whose assisted change has at least one assistant")
    val assistedBuilds: Int,
    @APIDescription("Builds created in the window whose assisted change is known - computed by Yontrack or set by the CI. The denominator of the assisted share.")
    val knownBuilds: Int,
    @APIDescription("Builds created in the window whose assisted change is unknown - excluded from the assisted share")
    val unknownBuilds: Int,
) {
    /**
     * Share of the known builds which are assisted, between 0 and 1 - `null` when no build is known.
     */
    @APIDescription("Share of the known builds which are assisted, between 0 and 1 - null when no build created in the window is known")
    val assistedShare: Double?
        get() = if (knownBuilds > 0) assistedBuilds.toDouble() / knownBuilds else null
}
