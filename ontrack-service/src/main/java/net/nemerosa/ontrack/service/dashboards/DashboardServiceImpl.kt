package net.nemerosa.ontrack.service.dashboards

import com.fasterxml.jackson.databind.node.NullNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.json.parse
import net.nemerosa.ontrack.model.dashboards.*
import net.nemerosa.ontrack.model.preferences.PreferencesService
import net.nemerosa.ontrack.model.security.SecurityService
import net.nemerosa.ontrack.model.security.isGlobalFunctionGranted
import net.nemerosa.ontrack.yaml.Yaml
import org.springframework.stereotype.Service
import java.util.*

@Service
class DashboardServiceImpl(
    private val securityService: SecurityService,
    private val preferencesService: PreferencesService,
    private val dashboardStorageService: DashboardStorageService,
) : DashboardService {

    override fun userDashboard(): Dashboard {
        val account = securityService.currentUser?.account
            ?: return DefaultDashboards.defaultDashboard
        val selectedUuid = preferencesService.getPreferences(account).dashboardUuid
            ?: return DefaultDashboards.defaultDashboard
        return dashboardStorageService.findDashboardByUuid(selectedUuid)
            ?: return DefaultDashboards.defaultDashboard
    }

    override fun userDashboards(): List<Dashboard> {
        val list = mutableListOf<Dashboard>()
        // Built-in dashboards
        list.addAll(DefaultDashboards.defaultDashboards)
        // Private dashboards
        securityService.currentUser?.account?.let {
            list.addAll(dashboardStorageService.findDashboardsByUser(it.id))
        }
        // Shared dashboards
        list.addAll(dashboardStorageService.findSharedDashboards())
        // Storing by name
        list.sortBy { it.name }
        // OK
        return list
    }

    override fun shareDashboard(input: ShareDashboardInput): Dashboard {
        // Checking the rights
        securityService.checkGlobalFunction(DashboardSharing::class.java)
        val userId = securityService.currentUser?.account?.id!!
        // Gets the existing dashboard
        val existing = dashboardStorageService.findDashboardsByUser(userId)
            .find { it.uuid == input.uuid }
            ?: throw DashboardUuidNotFoundException(input.uuid)
        // Checks the scope is private
        check(existing.userScope == DashboardContextUserScope.PRIVATE) {
            "Expected private dashboard."
        }
        // Updating
        return dashboardStorageService.saveDashboard(existing.share())
    }

    override fun saveDashboard(input: SaveDashboardInput): Dashboard {
        // No built-in
        if (input.userScope == DashboardContextUserScope.BUILT_IN) {
            throw DashboardCannotSaveBuiltInException()
        }
        // Checks rights to edit
        securityService.checkGlobalFunction(DashboardEdition::class.java)
        // Checks rights to share
        if (input.userScope != DashboardContextUserScope.PRIVATE) {
            securityService.checkGlobalFunction(DashboardSharing::class.java)
        }
        // Checks name unicity among accessible dashboards
        val existingDashboards = userDashboards()
        val existingDashboard = existingDashboards.find { it.name == input.name }
        if (existingDashboard != null && (input.uuid.isNullOrBlank() || input.uuid != existingDashboard.uuid)) {
            throw DashboardNameAlreadyExistsException(input.name)
        }
        // TODO Checks the widgets configurations
        // Fills the IDs
        val dashboard = Dashboard(
            uuid = input.uuid?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            name = input.name,
            userScope = input.userScope,
            widgets = input.widgets.map {
                WidgetInstance(
                    uuid = it.uuid
                        ?.takeIf { it.isNotBlank() }
                        ?.takeIf { !input.uuid.isNullOrBlank() }
                        ?: UUID.randomUUID().toString(),
                    key = it.key,
                    config = it.config,
                    layout = it.layout,
                )
            }
        )
        // Saves the dashboard
        dashboardStorageService.saveDashboard(dashboard)
        // Selection for the user
        if (input.select) {
            val account = securityService.currentUser?.account
            if (account != null) {
                preferencesService.savePreferences(account) {
                    it.dashboardUuid = dashboard.uuid
                }
            }
        }
        // OK
        return dashboard
    }

    /**
     * Deletion of an existing dashboard.
     *
     * A built-in dashboard can never be deleted.
     *
     * A shared dashboard can be deleted if:
     *
     * * the user is granted the [DashboardGlobal] function
     * * or the user owns this dashboard
     *
     * A private dashboard can be deleted if:
     *
     * * the user owns this dashboard
     */
    override fun deleteDashboard(uuid: String) {
        val existing = dashboardStorageService.findDashboardByUuid(uuid)
            ?: throw DashboardUuidNotFoundException(uuid)

        val account = securityService.currentUser?.account
        val ownsDashboard = account != null && dashboardStorageService.ownDashboard(uuid, account.id)

        val okToDelete: Boolean = when (existing.userScope) {
            DashboardContextUserScope.BUILT_IN -> throw DashboardCannotDeleteBuiltInException()
            DashboardContextUserScope.SHARED -> ownsDashboard || securityService.isGlobalFunctionGranted<DashboardGlobal>()
            DashboardContextUserScope.PRIVATE -> ownsDashboard
        }

        if (okToDelete) {

            if (account != null) {
                val prefs = preferencesService.getPreferences(account)
                if (prefs.dashboardUuid == uuid) {
                    prefs.dashboardUuid = null
                    preferencesService.setPreferences(account, prefs)
                }
            }

            dashboardStorageService.deleteDashboard(uuid)
        }
    }

    override fun selectDashboard(uuid: String) {
        val accessible = userDashboards().find { it.uuid == uuid }
            ?: throw DashboardUuidNotFoundException(uuid)
        val account = securityService.currentUser?.account
        if (account != null) {
            preferencesService.savePreferences(account) {
                it.dashboardUuid = accessible.uuid
            }
        }
    }

    override fun findDashboardByUuid(uuid: String): Dashboard? =
        dashboardStorageService.findDashboardByUuid(uuid)

    override fun upsertSharedDashboard(definition: DashboardDefinition): Dashboard {
        val definitionUuid = definition.uuid
        val existing = if (!definitionUuid.isNullOrBlank()) {
            dashboardStorageService.findDashboardByUuid(definitionUuid)
        } else {
            dashboardStorageService.findSharedDashboards().find { it.name == definition.name }
        }
        val resolvedUuid = existing?.uuid
            ?: definition.uuid?.takeIf { it.isNotBlank() }
            ?: UUID.nameUUIDFromBytes("dashboard:${definition.name}".toByteArray(Charsets.UTF_8)).toString()
        val widgets = definition.widgets.mapIndexed { idx, w ->
            WidgetInstance(
                uuid = w.uuid?.takeIf { it.isNotBlank() }
                    ?: UUID.nameUUIDFromBytes("widget:$resolvedUuid:$idx".toByteArray(Charsets.UTF_8)).toString(),
                key = w.key,
                config = w.config ?: NullNode.instance,
                layout = w.layout,
            )
        }
        val dashboard = Dashboard(
            uuid = resolvedUuid,
            name = definition.name,
            userScope = DashboardContextUserScope.SHARED,
            widgets = widgets,
        )
        dashboardStorageService.saveDashboard(dashboard)
        return dashboard
    }

    override fun applyDashboards(yaml: String): List<Dashboard> {
        securityService.checkGlobalFunction(DashboardSharing::class.java)
        val yamlUtil = Yaml()
        val documents = yamlUtil.read(yaml)
        val definitions = documents.flatMap { doc ->
            if (doc.isArray) {
                doc.map { it.parse<DashboardDefinition>() }
            } else {
                listOf(doc.parse())
            }
        }
        return definitions.map { upsertSharedDashboard(it) }
    }

    override fun dashboardAsYaml(dashboard: Dashboard): String {
        // No UUID is exported: they are identifiers local to one Yontrack installation and
        // are regenerated deterministically when the YAML is applied back (see [upsertSharedDashboard]).
        val definition = DashboardDefinition(
            name = dashboard.name,
            widgets = dashboard.widgets.map { w ->
                WidgetDefinition(
                    key = w.key,
                    layout = w.layout,
                    config = w.config.takeIf { !it.isNull },
                )
            },
        )
        val yamlUtil = Yaml()
        // The export is a collection of one dashboard, directly usable by [applyDashboards]
        val json = listOf(listOf(definition).asJson())
        return yamlUtil.write(json)
    }

    override fun getAuthorizations(dashboard: Dashboard): DashboardAuthorizations {
        val account = securityService.currentUser?.account
        return if (account != null) {
            val ownDashboard = dashboardStorageService.ownDashboard(dashboard.uuid, account.id)
            val editing = securityService.isGlobalFunctionGranted<DashboardEdition>()
            val sharing = securityService.isGlobalFunctionGranted<DashboardSharing>()
            val edit = (dashboard.userScope == DashboardContextUserScope.PRIVATE && ownDashboard && editing) ||
                    (dashboard.userScope == DashboardContextUserScope.SHARED && sharing)
            DashboardAuthorizations(
                edit = edit,
                share = dashboard.userScope == DashboardContextUserScope.PRIVATE && sharing,
                delete = edit,
            )
        } else {
            DashboardAuthorizations.NONE
        }
    }
}