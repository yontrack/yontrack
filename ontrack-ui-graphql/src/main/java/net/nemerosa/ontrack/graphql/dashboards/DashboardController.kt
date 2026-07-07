package net.nemerosa.ontrack.graphql.dashboards

import net.nemerosa.ontrack.common.UserException
import net.nemerosa.ontrack.graphql.DeletionPayload
import net.nemerosa.ontrack.graphql.payloads.toPayloadErrors
import net.nemerosa.ontrack.model.dashboards.*
import net.nemerosa.ontrack.model.dashboards.widgets.Widget
import net.nemerosa.ontrack.model.dashboards.widgets.WidgetService
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.MutationMapping
import org.springframework.graphql.data.method.annotation.QueryMapping
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

data class ApplyDashboardsInput(val yaml: String)

@Controller
class DashboardController(
    private val dashboardService: DashboardService,
    private val widgetService: WidgetService,
) {

    @QueryMapping
    fun userDashboard(): Dashboard = dashboardService.userDashboard()

    @QueryMapping
    fun userDashboards(): List<Dashboard> = dashboardService.userDashboards()

    @QueryMapping
    fun dashboardWidgets(): List<Widget<*>> = widgetService.findAll()

    @SchemaMapping
    fun authorizations(dashboard: Dashboard): DashboardAuthorizations = dashboardService.getAuthorizations(dashboard)

    @MutationMapping
    fun saveDashboard(@Argument input: SaveDashboardInput): SaveDashboardPayload =
        try {
            SaveDashboardPayload(dashboard = dashboardService.saveDashboard(input))
        } catch (any: UserException) {
            SaveDashboardPayload(any.toPayloadErrors())
        }

    @MutationMapping
    fun shareDashboard(@Argument input: ShareDashboardInput): ShareDashboardPayload =
        try {
            ShareDashboardPayload(dashboard = dashboardService.shareDashboard(input))
        } catch (any: UserException) {
            ShareDashboardPayload(any.toPayloadErrors())
        }

    @MutationMapping
    fun deleteDashboard(@Argument input: DeleteDashboardInput): DeletionPayload =
        try {
            dashboardService.deleteDashboard(input.uuid)
            DeletionPayload()
        } catch (any: UserException) {
            DeletionPayload(any.toPayloadErrors())
        }

    @MutationMapping
    fun selectDashboard(@Argument input: SelectDashboardInput): SelectDashboardPayload =
        try {
            dashboardService.selectDashboard(input.uuid)
            SelectDashboardPayload()
        } catch (any: UserException) {
            SelectDashboardPayload(any.toPayloadErrors())
        }

    @MutationMapping
    fun applyDashboards(@Argument input: ApplyDashboardsInput): ApplyDashboardsPayload =
        try {
            val dashboards = dashboardService.applyDashboards(input.yaml)
            ApplyDashboardsPayload(dashboards = dashboards)
        } catch (any: UserException) {
            ApplyDashboardsPayload(any.toPayloadErrors())
        }

    @SchemaMapping(typeName = "Dashboard", field = "asYaml")
    fun asYaml(dashboard: Dashboard): String = dashboardService.dashboardAsYaml(dashboard)

}