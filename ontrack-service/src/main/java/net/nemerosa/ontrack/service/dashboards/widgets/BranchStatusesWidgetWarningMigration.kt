package net.nemerosa.ontrack.service.dashboards.widgets

import tools.jackson.databind.node.ObjectNode
import net.nemerosa.ontrack.json.asJson
import net.nemerosa.ontrack.model.dashboards.widgets.BranchStatusesWidget
import net.nemerosa.ontrack.model.dashboards.widgets.BranchStatusesWidgetConfig
import net.nemerosa.ontrack.model.support.StartupService
import net.nemerosa.ontrack.model.support.StorageService
import net.nemerosa.ontrack.service.dashboards.DashboardStorageService
import net.nemerosa.ontrack.service.migrations.AbstractMigration
import org.springframework.stereotype.Component

/**
 * Migration from simple promotion/validation names to lists with warnings periods.
 */
@Component
class BranchStatusesWidgetWarningMigration(
    storageService: StorageService,
    private val branchStatusesWidget: BranchStatusesWidget,
    private val dashboardStorageService: DashboardStorageService,
) : AbstractMigration(
    storageService
) {

    override fun getName(): String = "Branch statuses widget warning migration"

    override fun startupOrder(): Int = StartupService.JOB_REGISTRATION

    override fun migration() {
        dashboardStorageService.migrateDashboards { dashboard ->
            dashboard.migrateWidgets { widget ->
                if (widget.key == branchStatusesWidget.key) {
                    widget.adaptConfig { cfg ->
                        val config = cfg as ObjectNode
                        val promotionNames = config.path("promotions").values().map { it.asText() }
                        val validationNames = config.path("validations").values().map { it.asText() }
                        config.remove("promotions")
                        config.remove("validations")
                        config.set(
                            "promotionConfigs",
                            promotionNames.map { name ->
                                BranchStatusesWidgetConfig.PromotionConfig(
                                    promotionLevel = name,
                                    period = null
                                )
                            }.asJson()
                        )
                        config.set(
                            "validationConfigs",
                            validationNames.map { name ->
                                BranchStatusesWidgetConfig.ValidationConfig(
                                    validationStamp = name,
                                    period = null
                                )
                            }.asJson()
                        )
                        config
                    }
                } else {
                    widget
                }
            }
        }
    }
}