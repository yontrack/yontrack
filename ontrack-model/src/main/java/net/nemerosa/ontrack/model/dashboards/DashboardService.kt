package net.nemerosa.ontrack.model.dashboards

interface DashboardService {

    /**
     * Returns the default dashboard for the current user.
     */
    fun userDashboard(): Dashboard

    /**
     * Returns the list of dashboards which are accessible to the current user.
     */
    fun userDashboards(): List<Dashboard>

    /**
     * Saves a dashboard
     */
    fun saveDashboard(input: SaveDashboardInput): Dashboard

    /**
     * Shares a dashboard
     */
    fun shareDashboard(input: ShareDashboardInput): Dashboard

    /**
     * Deletes a dashboard
     */
    fun deleteDashboard(uuid: String)

    /**
     * Selects a dashboard for the current user
     */
    fun selectDashboard(uuid: String)

    /**
     * Gets the authorizations for a given dashboard
     */
    fun getAuthorizations(dashboard: Dashboard): DashboardAuthorizations

    /**
     * Finds a dashboard by UUID regardless of scope. Returns null if not found.
     */
    fun findDashboardByUuid(uuid: String): Dashboard?

    /**
     * Upserts a SHARED dashboard from a definition. Idempotent: matches by UUID then by name.
     * Generates a deterministic UUID from the name when none is supplied. Never deletes others.
     */
    fun upsertSharedDashboard(definition: DashboardDefinition): Dashboard

    /**
     * Applies a YAML list of dashboard definitions, creating or updating SHARED dashboards.
     * Requires [DashboardSharing] permission. Never deletes dashboards not in the list.
     */
    fun applyDashboards(yaml: String): List<Dashboard>

    /**
     * Serializes a dashboard to the YAML format accepted by [applyDashboards].
     */
    fun dashboardAsYaml(dashboard: Dashboard): String

}