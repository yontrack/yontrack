package net.nemerosa.ontrack.extension.agents.activity

/**
 * How much of the delivery the agents drive, counted over a window of days (#2035) - the tiles of the
 * *Agent activity* dashboard widget.
 *
 * Under the licence of the agent governance (`extension.agents`): without it, every read is refused.
 */
interface AgentActivityStatsService {

    /**
     * Counts the actions of the agents and the assisted builds over a window, across the projects the
     * current user can see - narrowed by the [filter].
     *
     * The actions are counted as [AgentActionsService.findAgentActions] reads them: an event concerning
     * a project the user cannot see, as its project or as its extra project, is not counted. Every
     * count is an aggregate query.
     *
     * @param filter Window and narrowing
     * @return Counts
     * @throws net.nemerosa.ontrack.extension.license.control.LicenseFeatureException When the licence
     * does not allow the agent governance
     */
    fun getAgentActivityStats(filter: AgentActivityStatsFilter): AgentActivityStats
}
