package net.nemerosa.ontrack.kdsl.spec.dashboards

import com.apollographql.apollo.api.Optional
import com.fasterxml.jackson.databind.JsonNode
import net.nemerosa.ontrack.kdsl.connector.graphql.convert
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.DeleteDashboardMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.SaveDashboardMutation
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.UserDashboardsQuery
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.DashboardContextUserScope
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.WidgetInstanceInput
import net.nemerosa.ontrack.kdsl.connector.graphql.schema.type.WidgetLayoutInput
import net.nemerosa.ontrack.kdsl.connector.graphqlConnector
import net.nemerosa.ontrack.kdsl.spec.Ontrack

/**
 * Saves a dashboard, creating it or replacing the one already carrying [uuid].
 *
 * Yontrack refuses a second dashboard with the same name unless the UUID matches, so
 * passing a stable UUID is what makes saving the same dashboard twice an update.
 */
fun Ontrack.saveDashboard(
    uuid: String,
    name: String,
    widgets: List<DashboardWidget>,
    userScope: DashboardContextUserScope = DashboardContextUserScope.SHARED,
) {
    graphqlConnector.mutate(
        SaveDashboardMutation(
            uuid = uuid,
            name = name,
            userScope = userScope,
            widgets = widgets.map {
                WidgetInstanceInput(
                    uuid = Optional.presentIfNotNull(it.uuid),
                    key = it.key,
                    config = it.config,
                    layout = WidgetLayoutInput(
                        x = it.layout.x,
                        y = it.layout.y,
                        w = it.layout.w,
                        h = it.layout.h,
                    ),
                )
            },
        )
    ) { it?.saveDashboard?.payloadInterfaceUserErrors?.convert() }
}

/**
 * One widget on a dashboard.
 *
 * @property uuid Kept across saves, so that a re-saved dashboard keeps the same widgets
 * rather than a new set of them.
 * @property key Widget type, such as `home/LastActiveProjects`.
 * @property config Widget configuration, whose shape depends on [key].
 */
data class DashboardWidget(
    val uuid: String,
    val key: String,
    val config: JsonNode,
    val layout: DashboardWidgetLayout,
)

/**
 * Where a widget sits on the dashboard grid, which is 12 columns wide.
 */
data class DashboardWidgetLayout(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

/**
 * The dashboards this account can see: the built-in one, the shared ones, and its own
 * private ones. Another account's private dashboards are not visible and cannot be listed.
 */
fun Ontrack.dashboards(): List<Dashboard> =
    graphqlConnector.query(UserDashboardsQuery())
        ?.userDashboards
        ?.map {
            Dashboard(
                uuid = it.uuid,
                name = it.name,
                userScope = it.userScope,
            )
        }
        ?: emptyList()

/**
 * Deletes a dashboard. The built-in one cannot be deleted.
 */
fun Ontrack.deleteDashboard(uuid: String) {
    graphqlConnector.mutate(
        DeleteDashboardMutation(uuid)
    ) { it?.deleteDashboard?.payloadInterfaceUserErrors?.convert() }
}

data class Dashboard(
    val uuid: String,
    val name: String,
    val userScope: DashboardContextUserScope,
)
